package org.infinispan.loaders.cassandra;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dataforte.cassandra.pool.DataSource;

import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.CfDef;
import org.apache.cassandra.thrift.Column;
import org.apache.cassandra.thrift.ColumnOrSuperColumn;
import org.apache.cassandra.thrift.ColumnParent;
import org.apache.cassandra.thrift.ColumnPath;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.Deletion;
import org.apache.cassandra.thrift.KeyRange;
import org.apache.cassandra.thrift.KeySlice;
import org.apache.cassandra.thrift.KsDef;
import org.apache.cassandra.thrift.Mutation;
import org.apache.cassandra.thrift.NotFoundException;
import org.apache.cassandra.thrift.SlicePredicate;
import org.apache.cassandra.thrift.SliceRange;
import org.apache.cassandra.thrift.SuperColumn;
import org.infinispan.Cache;
import org.infinispan.commons.CacheConfigurationException;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.container.entries.InternalCacheValue;
import org.infinispan.loaders.AbstractCacheStore;
import org.infinispan.loaders.CacheLoaderException;
import org.infinispan.loaders.cassandra.configuration.CassandraCacheStoreConfiguration;
import org.infinispan.loaders.cassandra.logging.Log;
import org.infinispan.loaders.keymappers.TwoWayKey2StringMapper;
import org.infinispan.loaders.keymappers.UnsupportedKeyTypeException;
import org.infinispan.commons.marshall.StreamingMarshaller;
import org.infinispan.commons.util.Util;
import org.infinispan.util.logging.LogFactory;

/**
 * A persistent <code>CacheLoader</code> based on Apache Cassandra project. See
 * http://cassandra.apache.org/
 *
 * @author Tristan Tarrant
 */
public class CassandraCacheStore <T extends CassandraCacheStoreConfiguration> extends AbstractCacheStore <T> {

   private static final String ENTRY_KEY_PREFIX = "entry_";
   private static final String ENTRY_COLUMN_NAME = "entry";
   private static final String EXPIRATION_KEY = "expiration";
   private static final int SLICE_SIZE = 100;
   private static final Log log = LogFactory.getLog(CassandraCacheStore.class, Log.class);
   private static final boolean trace = log.isTraceEnabled();

   private DataSource dataSource;

   private ConsistencyLevel readConsistencyLevel;
   private ConsistencyLevel writeConsistencyLevel;

   private String cacheName;
   private ColumnPath entryColumnPath;
   private ColumnParent entryColumnParent;
   private ColumnParent expirationColumnParent;
   private String entryKeyPrefix;
   private ByteBuffer expirationKey;
   private TwoWayKey2StringMapper keyMapper;

   static private Charset UTF8Charset = Charset.forName("UTF-8");

   @Override
   public void init(T configuration, Cache<?, ?> cache, StreamingMarshaller m)
            throws CacheLoaderException {
      super.init(configuration, cache, m);
      this.cacheName = cache.getName();
   }

   @Override
   public void start() throws CacheLoaderException {

      try {
         if (!configuration.autoCreateKeyspace())
            configuration.poolProperties.setKeySpace(configuration.keySpace());
         dataSource = new DataSource(configuration.getPoolProperties());
         readConsistencyLevel = configuration.readConsistencyLevel();
         writeConsistencyLevel = configuration.writeConsistencyLevel();
         entryColumnPath = new ColumnPath(configuration.entryColumnFamily()).setColumn(ENTRY_COLUMN_NAME
                  .getBytes(UTF8Charset));
         entryColumnParent = new ColumnParent(configuration.entryColumnFamily());
         entryKeyPrefix = ENTRY_KEY_PREFIX + (configuration.sharedKeyspace() ? cacheName + "_" : "");
         expirationColumnParent = new ColumnParent(configuration.expirationColumnFamily());
         expirationKey = ByteBufferUtil.bytes(EXPIRATION_KEY
                  + (configuration.sharedKeyspace() ? "_" + cacheName : ""));
         keyMapper = (TwoWayKey2StringMapper) Util.getInstance(configuration.keyMapper(),
               configuration.getClass().getClassLoader());
      } catch (Exception e) {
         throw new CacheConfigurationException(e);
      }

      if (configuration.autoCreateKeyspace()) {
         log.debug("automatically create keyspace");
         try {
            createKeySpace();
         } finally {
            dataSource.close(); // Make sure all connections are closed
         }
         dataSource.setKeySpace(configuration.keySpace()); // Set the keyspace we have
                                                  // created
      }

      log.debug("cleaning up expired entries...");
      purgeInternal();

      log.debug("started");
      super.start();
   }

   private void createKeySpace() throws CacheLoaderException {
      Cassandra.Client cassandraClient = null;
      try {
         cassandraClient = dataSource.getConnection();
         // check if the keyspace exists
         try {
            cassandraClient.describe_keyspace(configuration.keySpace());
            return;
         } catch (NotFoundException e) {
            KsDef keySpace = new KsDef();
            keySpace.setName(configuration.keySpace());
            keySpace.setStrategy_class("org.apache.cassandra.locator.SimpleStrategy");
            Map<String, String> strategy_options = new HashMap<String, String>();
            strategy_options.put("replication_factor", "1");
            keySpace.setStrategy_options(strategy_options);

            CfDef entryCF = new CfDef();
            entryCF.setName(configuration.entryColumnFamily());
            entryCF.setKeyspace(configuration.keySpace());
            entryCF.setComparator_type("BytesType");
            keySpace.addToCf_defs(entryCF);

            CfDef expirationCF = new CfDef();
            expirationCF.setName(configuration.expirationColumnFamily());
            expirationCF.setKeyspace(configuration.keySpace());
            expirationCF.setColumn_type("Super");
            expirationCF.setComparator_type("LongType");
            expirationCF.setSubcomparator_type("BytesType");
            keySpace.addToCf_defs(expirationCF);

            cassandraClient.system_add_keyspace(keySpace);
         }
      } catch (Exception e) {
         throw new CacheLoaderException("Could not create keyspace/column families", e);
      } finally {
         dataSource.releaseConnection(cassandraClient);
      }

   }

   @Override
   public InternalCacheEntry load(Object key) throws CacheLoaderException {
      String hashKey = hashKey(key);
      Cassandra.Client cassandraClient = null;
      try {
         cassandraClient = dataSource.getConnection();
         ColumnOrSuperColumn column = cassandraClient.get(ByteBufferUtil.bytes(hashKey),
                  entryColumnPath, readConsistencyLevel);
         InternalCacheEntry ice = unmarshall(column.getColumn().getValue(), key);
         if (ice != null && ice.isExpired(timeService.wallClockTime())) {
            remove(key);
            return null;
         }
         return ice;
      } catch (NotFoundException nfe) {
         log.debugf("Key '%s' not found", hashKey);
         return null;
      } catch (Exception e) {
         throw new CacheLoaderException(e);
      } finally {
         dataSource.releaseConnection(cassandraClient);
      }
   }

   @Override
   public Set<InternalCacheEntry> loadAll() throws CacheLoaderException {
      return load(Integer.MAX_VALUE);
   }

   @Override
   public Set<InternalCacheEntry> load(int numEntries) throws CacheLoaderException {
      Cassandra.Client cassandraClient = null;
      try {
         cassandraClient = dataSource.getConnection();
         Set<InternalCacheEntry> s = new HashSet<InternalCacheEntry>();
         SlicePredicate slicePredicate = new SlicePredicate();
         slicePredicate.setSlice_range(new SliceRange(ByteBuffer.wrap(entryColumnPath.getColumn()),
                  ByteBufferUtil.EMPTY_BYTE_BUFFER, false, 1));
         String startKey = "";

         // Get the keys in SLICE_SIZE blocks
         int sliceSize = Math.min(SLICE_SIZE, numEntries);
         for (boolean complete = false; !complete;) {
            KeyRange keyRange = new KeyRange(sliceSize);
            keyRange.setStart_token(startKey);
            keyRange.setEnd_token("");
            List<KeySlice> keySlices = cassandraClient.get_range_slices(entryColumnParent,
                     slicePredicate, keyRange, readConsistencyLevel);

            // Cycle through all the keys
            for (KeySlice keySlice : keySlices) {
               Object key = unhashKey(keySlice.getKey());
               if (key == null) // Skip invalid keys
                  continue;
               List<ColumnOrSuperColumn> columns = keySlice.getColumns();
               if (columns.size() > 0) {
                  if (log.isDebugEnabled()) {
                     log.debugf("Loading %s", key);
                  }
                  byte[] value = columns.get(0).getColumn().getValue();
                  InternalCacheEntry ice = unmarshall(value, key);
                  s.add(ice);
               } else if (log.isDebugEnabled()) {
                  log.debugf("Skipping empty key %s", key);
               }
            }
            if (keySlices.size() < sliceSize) {
               // Cassandra has returned less keys than what we asked for.
               // Assume we have finished
               complete = true;
            } else {
               // Cassandra has returned exactly the amount of keys we
               // asked for. If we haven't reached the required quota yet,
               // assume we need to cycle again starting from
               // the last returned key (excluded)
               sliceSize = Math.min(SLICE_SIZE, numEntries - s.size());
               if (sliceSize == 0) {
                  complete = true;
               } else {
                  startKey = new String(keySlices.get(keySlices.size() - 1).getKey(), UTF8Charset);
               }
            }

         }
         return s;
      } catch (Exception e) {
         throw new CacheLoaderException(e);
      } finally {
         dataSource.releaseConnection(cassandraClient);
      }
   }

   @Override
   public Set<Object> loadAllKeys(Set<Object> keysToExclude) throws CacheLoaderException {
      Cassandra.Client cassandraClient = null;
      try {
         cassandraClient = dataSource.getConnection();
         Set<Object> s = new HashSet<Object>();
         SlicePredicate slicePredicate = new SlicePredicate();
         slicePredicate.setSlice_range(new SliceRange(ByteBuffer.wrap(entryColumnPath.getColumn()),
                  ByteBufferUtil.EMPTY_BYTE_BUFFER, false, 1));
         String startKey = "";
         boolean complete = false;
         // Get the keys in SLICE_SIZE blocks
         while (!complete) {
            KeyRange keyRange = new KeyRange(SLICE_SIZE);
            keyRange.setStart_token(startKey);
            keyRange.setEnd_token("");
            List<KeySlice> keySlices = cassandraClient.get_range_slices(entryColumnParent,
                     slicePredicate, keyRange, readConsistencyLevel);
            if (keySlices.size() < SLICE_SIZE) {
               complete = true;
            } else {
               startKey = new String(keySlices.get(keySlices.size() - 1).getKey(), UTF8Charset);
            }

            for (KeySlice keySlice : keySlices) {
               if (keySlice.getColumnsSize() > 0) {
                  Object key = unhashKey(keySlice.getKey());
                  if (key != null && (keysToExclude == null || !keysToExclude.contains(key)))
                     s.add(key);
               }
            }
         }
         return s;
      } catch (Exception e) {
         throw new CacheLoaderException(e);
      } finally {
         dataSource.releaseConnection(cassandraClient);
      }
   }

   /**
    * Closes all databases, ignoring exceptions, and nulls references to all database related
    * information.
    */
   @Override
   public void stop() throws CacheLoaderException {
      super.stop();
   }

   @Override
   public void clear() throws CacheLoaderException {
      Cassandra.Client cassandraClient = null;
      try {
         cassandraClient = dataSource.getConnection();
         SlicePredicate slicePredicate = new SlicePredicate();
         slicePredicate.setSlice_range(new SliceRange(ByteBuffer.wrap(entryColumnPath.getColumn()),
                  ByteBufferUtil.EMPTY_BYTE_BUFFER, false, 1));
         String startKey = "";
         boolean complete = false;
         // Get the keys in SLICE_SIZE blocks
         while (!complete) {
            KeyRange keyRange = new KeyRange(SLICE_SIZE);
            keyRange.setStart_token(startKey);
            keyRange.setEnd_token("");
            List<KeySlice> keySlices = cassandraClient.get_range_slices(entryColumnParent,
                     slicePredicate, keyRange, readConsistencyLevel);
            if (keySlices.size() < SLICE_SIZE) {
               complete = true;
            } else {
               startKey = new String(keySlices.get(keySlices.size() - 1).getKey(), UTF8Charset);
            }
            Map<ByteBuffer, Map<String, List<Mutation>>> mutationMap = new HashMap<ByteBuffer, Map<String, List<Mutation>>>();

            for (KeySlice keySlice : keySlices) {
               remove0(ByteBuffer.wrap(keySlice.getKey()), mutationMap);
            }
            cassandraClient.batch_mutate(mutationMap, ConsistencyLevel.ALL);
         }
      } catch (Exception e) {
         throw new CacheLoaderException(e);
      } finally {
         dataSource.releaseConnection(cassandraClient);
      }

   }

   @Override
   public boolean remove(Object key) throws CacheLoaderException {
      if (trace)
         log.tracef("remove(\"%s\") ", key);
      String hashKey = hashKey(key);
      Cassandra.Client cassandraClient = null;
      try {
         cassandraClient = dataSource.getConnection();
         // Check if the key exists before attempting to remove it
         try {
        	 cassandraClient.get(ByteBufferUtil.bytes(hashKey), entryColumnPath, readConsistencyLevel);
         } catch (NotFoundException e) {
        	 return false;
         }
         Map<ByteBuffer, Map<String, List<Mutation>>> mutationMap = new HashMap<ByteBuffer, Map<String, List<Mutation>>>();
         remove0(ByteBufferUtil.bytes(hashKey), mutationMap);
         cassandraClient.batch_mutate(mutationMap, writeConsistencyLevel);
         return true;
      } catch (Exception e) {
         log.errorRemovingKey(key, e);
         return false;
      } finally {
         dataSource.releaseConnection(cassandraClient);
      }
   }

   private void remove0(ByteBuffer key, Map<ByteBuffer, Map<String, List<Mutation>>> mutationMap) {
      addMutation(mutationMap, key, configuration.entryColumnFamily(), null, null);
   }

   private byte[] marshall(InternalCacheEntry entry) throws IOException, InterruptedException {
      return getMarshaller().objectToByteBuffer(entry.toInternalCacheValue());
   }

   private InternalCacheEntry unmarshall(Object o, Object key) throws IOException,
            ClassNotFoundException {
      if (o == null)
         return null;
      byte b[] = (byte[]) o;
      InternalCacheValue v = (InternalCacheValue) getMarshaller().objectFromByteBuffer(b);
      return v.toInternalCacheEntry(key);
   }

   @Override
   public void store(InternalCacheEntry entry) throws CacheLoaderException {
      Cassandra.Client cassandraClient = null;

      try {
         cassandraClient = dataSource.getConnection();
         Map<ByteBuffer, Map<String, List<Mutation>>> mutationMap = new HashMap<ByteBuffer, Map<String, List<Mutation>>>();
         store0(entry, mutationMap);

         cassandraClient.batch_mutate(mutationMap, writeConsistencyLevel);
      } catch (Exception e) {
         throw new CacheLoaderException(e);
      } finally {
         dataSource.releaseConnection(cassandraClient);
      }
   }

   private void store0(InternalCacheEntry entry,
            Map<ByteBuffer, Map<String, List<Mutation>>> mutationMap) throws IOException,
            UnsupportedKeyTypeException {
      Object key = entry.getKey();
      if (trace)
         log.tracef("store(\"%s\") ", key);
      String cassandraKey = hashKey(key);
      try {
         addMutation(mutationMap, ByteBufferUtil.bytes(cassandraKey), configuration.entryColumnFamily(),
                  ByteBuffer.wrap(entryColumnPath.getColumn()), ByteBuffer.wrap(marshall(entry)));
         if (entry.canExpire()) {
            addExpiryEntry(cassandraKey, entry.getExpiryTime(), mutationMap);
         }
      } catch (InterruptedException ie) {
         if (trace)
            log.trace("Interrupted while trying to marshall entry");
         Thread.currentThread().interrupt();
      }
   }

   private void addExpiryEntry(String cassandraKey, long expiryTime,
            Map<ByteBuffer, Map<String, List<Mutation>>> mutationMap) {
      try {
         addMutation(mutationMap, expirationKey, configuration.expirationColumnFamily(),
                  ByteBufferUtil.bytes(expiryTime), ByteBufferUtil.bytes(cassandraKey),
                  ByteBufferUtil.EMPTY_BYTE_BUFFER);
      } catch (Exception e) {
         // Should not happen
      }
   }

   /**
    * Writes to a stream the number of entries (long) then the entries themselves.
    */
   @Override
   public void toStream(ObjectOutput out) throws CacheLoaderException {
      try {
         Set<InternalCacheEntry> loadAll = loadAll();
         int count = 0;
         for (InternalCacheEntry entry : loadAll) {
            getMarshaller().objectToObjectStream(entry, out);
            count++;
         }
         getMarshaller().objectToObjectStream(null, out);
      } catch (IOException e) {
         throw new CacheLoaderException(e);
      }
   }

   /**
    * Reads from a stream the number of entries (long) then the entries themselves.
    */
   @Override
   public void fromStream(ObjectInput in) throws CacheLoaderException {
      try {
         int count = 0;
         while (true) {
            count++;
            InternalCacheEntry entry = (InternalCacheEntry) getMarshaller().objectFromObjectStream(
                     in);
            if (entry == null)
               break;
            store(entry);
         }
      } catch (IOException e) {
         throw new CacheLoaderException(e);
      } catch (ClassNotFoundException e) {
         throw new CacheLoaderException(e);
      } catch (InterruptedException ie) {
         if (log.isTraceEnabled())
            log.trace("Interrupted while reading from stream");
         Thread.currentThread().interrupt();
      }
   }

   /**
    * Purge expired entries. Expiration entries are stored in a single key (expirationKey) within a
    * specific ColumnFamily (set by configurationuration). The entries are grouped by expiration timestamp
    * in SuperColumns within which each entry's key is mapped to a column
    */
   @Override
   protected void purgeInternal() throws CacheLoaderException {
      if (trace)
         log.trace("purgeInternal");
      Cassandra.Client cassandraClient = null;
      try {
         cassandraClient = dataSource.getConnection();
         // We need to get all supercolumns from the beginning of time until
         // now, in SLICE_SIZE chunks
         SlicePredicate predicate = new SlicePredicate();
         predicate.setSlice_range(new SliceRange(ByteBufferUtil.EMPTY_BYTE_BUFFER, ByteBufferUtil
                  .bytes(timeService.wallClockTime()), false, SLICE_SIZE));

         for (boolean complete = false; !complete;) {
            Map<ByteBuffer, Map<String, List<Mutation>>> mutationMap = new HashMap<ByteBuffer, Map<String, List<Mutation>>>(SLICE_SIZE);
            // Get all columns
            List<ColumnOrSuperColumn> slice = cassandraClient.get_slice(expirationKey,
                     expirationColumnParent, predicate, readConsistencyLevel);
            complete = slice.size() < SLICE_SIZE;
            // Delete all keys returned by the slice
            for (ColumnOrSuperColumn crumb : slice) {
               SuperColumn scol = crumb.getSuper_column();
               for (Iterator<Column> i = scol.getColumnsIterator(); i.hasNext();) {
                  Column col = i.next();
                  // Remove the entry row
                  remove0(ByteBuffer.wrap(col.getName()), mutationMap);
               }
               // Remove the expiration supercolumn
               addMutation(mutationMap, expirationKey, configuration.expirationColumnFamily(),
                        ByteBuffer.wrap(scol.getName()), null, null);
            }
            cassandraClient.batch_mutate(mutationMap, writeConsistencyLevel);
         }
      } catch (Exception e) {
         throw new CacheLoaderException(e);
      } finally {
         dataSource.releaseConnection(cassandraClient);
      }

   }

   @Override
   public String toString() {
      return "CassandraCacheStore";
   }

   private String hashKey(Object key) throws UnsupportedKeyTypeException {
      if (!keyMapper.isSupportedType(key.getClass())) {
         throw new UnsupportedKeyTypeException(key);
      }

      return entryKeyPrefix + keyMapper.getStringMapping(key);
   }

   private Object unhashKey(byte[] key) {
      String skey = new String(key, UTF8Charset);

      if (skey.startsWith(entryKeyPrefix))
         return keyMapper.getKeyMapping(skey.substring(entryKeyPrefix.length()));
      else
         return null;
   }

   private static void addMutation(Map<ByteBuffer, Map<String, List<Mutation>>> mutationMap,
            ByteBuffer key, String columnFamily, ByteBuffer column, ByteBuffer value) {
      addMutation(mutationMap, key, columnFamily, null, column, value);
   }

   private static void addMutation(Map<ByteBuffer, Map<String, List<Mutation>>> mutationMap,
            ByteBuffer key, String columnFamily, ByteBuffer superColumn, ByteBuffer columnName,
            ByteBuffer value) {
      Map<String, List<Mutation>> keyMutations = mutationMap.get(key);
      // If the key doesn't exist yet, create the mutation holder
      if (keyMutations == null) {
         keyMutations = new HashMap<String, List<Mutation>>();
         mutationMap.put(key, keyMutations);
      }
      // If the columnfamily doesn't exist yet, create the mutation holder
      List<Mutation> columnFamilyMutations = keyMutations.get(columnFamily);
      if (columnFamilyMutations == null) {
         columnFamilyMutations = new ArrayList<Mutation>();
         keyMutations.put(columnFamily, columnFamilyMutations);
      }

      if (value == null) { // Delete
         Deletion deletion = new Deletion();
         deletion.setTimestamp(microTimestamp());
         if (superColumn != null) {
            deletion.setSuper_column(superColumn);
         }
         if (columnName != null) { // Single column delete
            deletion.setPredicate(new SlicePredicate().setColumn_names(Collections
                     .singletonList(columnName)));
         } // else Delete entire column family or supercolumn
         columnFamilyMutations.add(new Mutation().setDeletion(deletion));
      } else { // Insert/update
         ColumnOrSuperColumn cosc = new ColumnOrSuperColumn();
         if (superColumn != null) {
            List<Column> columns = new ArrayList<Column>(1);
            Column col = new Column(columnName);
            col.setValue(value);
            col.setTimestamp(microTimestamp());
            columns.add(col);
            cosc.setSuper_column(new SuperColumn(superColumn, columns));
         } else {
            Column col = new Column(columnName);
            col.setValue(value);
            col.setTimestamp(microTimestamp());
            cosc.setColumn(col);
         }
         columnFamilyMutations.add(new Mutation().setColumn_or_supercolumn(cosc));
      }
   }

   private static long microTimestamp() {
      return System.currentTimeMillis() * 1000l;
   }
}
