package org.infinispan.loaders.cassandra.configuration;

import java.util.Collections;
import java.util.List;

import org.apache.cassandra.thrift.ConsistencyLevel;
import org.infinispan.configuration.cache.AbstractStoreConfiguration;
import org.infinispan.configuration.cache.AsyncStoreConfiguration;
import org.infinispan.configuration.cache.SingletonStoreConfiguration;
import org.infinispan.commons.configuration.BuiltBy;
import org.infinispan.commons.util.TypedProperties;

@BuiltBy(CassandraCacheStoreConfigurationBuilder.class)
public class CassandraCacheStoreConfiguration extends AbstractStoreConfiguration {

   private final boolean autoCreateKeyspace;
   private final String configurationPropertiesFile;
   private final String entryColumnFamily;
   private final String expirationColumnFamily;
   private final boolean framed;
   private final List<CassandraServerConfiguration> servers;
   private final String keyMapper;
   private final String keySpace;
   private final String password;
   private final ConsistencyLevel readConsistencyLevel;
   private final boolean sharedKeyspace;
   private final String username;
   private final ConsistencyLevel writeConsistencyLevel;

   public CassandraCacheStoreConfiguration(boolean autoCreateKeyspace, String configurationPropertiesFile,
         String entryColumnFamily, String expirationColumnFamily, boolean framed, List<CassandraServerConfiguration> servers, String keyMapper,
         String keySpace, String password, boolean sharedKeyspace, String username,
         ConsistencyLevel readConsistencyLevel, ConsistencyLevel writeConsistencyLevel, boolean purgeOnStartup,
         boolean purgeSynchronously, int purgerThreads, boolean fetchPersistentState, boolean ignoreModifications,
         TypedProperties properties, AsyncStoreConfiguration asyncStoreConfiguration,
         SingletonStoreConfiguration singletonStoreConfiguration) {
      super(purgeOnStartup, purgeSynchronously, purgerThreads, fetchPersistentState, ignoreModifications, properties,
            asyncStoreConfiguration, singletonStoreConfiguration);
      this.autoCreateKeyspace = autoCreateKeyspace;
      this.configurationPropertiesFile = configurationPropertiesFile;
      this.entryColumnFamily = entryColumnFamily;
      this.expirationColumnFamily = expirationColumnFamily;
      this.framed = framed;
      this.servers = Collections.unmodifiableList(servers);
      this.keyMapper = keyMapper;
      this.keySpace = keySpace;
      this.password = password;
      this.sharedKeyspace = sharedKeyspace;
      this.username = username;
      this.readConsistencyLevel = readConsistencyLevel;
      this.writeConsistencyLevel = writeConsistencyLevel;
   }

   public boolean autoCreateKeyspace() {
      return autoCreateKeyspace;
   }

   public String configurationPropertiesFile() {
      return configurationPropertiesFile;
   }

   public String entryColumnFamily() {
      return entryColumnFamily;
   }

   public String expirationColumnFamily() {
      return expirationColumnFamily;
   }

   public boolean framed() {
      return framed;
   }

   public String keyMapper() {
      return keyMapper;
   }

   public List<CassandraServerConfiguration> hosts() {
      return servers;
   }

   public String keySpace() {
      return keySpace;
   }

   public List<CassandraServerConfiguration> servers() {
      return servers;
   }


   public boolean sharedKeyspace() {
      return sharedKeyspace;
   }

   public String password() {
      return password;
   }

   public String username() {
      return username;
   }

   public ConsistencyLevel readConsistencyLevel() {
      return readConsistencyLevel;
   }

   public ConsistencyLevel writeConsistencyLevel() {
      return writeConsistencyLevel;
   }
}
