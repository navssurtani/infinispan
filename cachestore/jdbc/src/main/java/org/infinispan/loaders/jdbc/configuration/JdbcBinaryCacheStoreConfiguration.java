package org.infinispan.loaders.jdbc.configuration;

import org.infinispan.configuration.cache.AsyncStoreConfiguration;
import org.infinispan.configuration.cache.SingletonStoreConfiguration;
import org.infinispan.commons.configuration.BuiltBy;
import org.infinispan.commons.util.TypedProperties;

@BuiltBy(JdbcBinaryCacheStoreConfigurationBuilder.class)
public class JdbcBinaryCacheStoreConfiguration extends AbstractJdbcCacheStoreConfiguration {

   private final TableManipulationConfiguration table;

   JdbcBinaryCacheStoreConfiguration(TableManipulationConfiguration table, ConnectionFactoryConfiguration connectionFactory, long lockAcquistionTimeout, int lockConcurrencyLevel,
         boolean purgeOnStartup, boolean purgeSynchronously, int purgerThreads, boolean fetchPersistentState, boolean ignoreModifications, TypedProperties properties,
         AsyncStoreConfiguration async, SingletonStoreConfiguration singletonStore) {
      super(connectionFactory, lockAcquistionTimeout, lockConcurrencyLevel, purgeOnStartup, purgeSynchronously, purgerThreads, fetchPersistentState, ignoreModifications,
            properties, async, singletonStore);
      this.table = table;
   }

   public TableManipulationConfiguration table() {
      return table;
   }

   @Override
   public String toString() {
      return "JdbcBinaryCacheStoreConfiguration [table=" + table + ", connectionFactory()=" + connectionFactory() + ", lockAcquistionTimeout()=" + lockAcquistionTimeout()
            + ", lockConcurrencyLevel()=" + lockConcurrencyLevel() + ", async()=" + async() + ", singletonStore()=" + singletonStore() + ", purgeOnStartup()=" + purgeOnStartup()
            + ", purgeSynchronously()=" + purgeSynchronously() + ", purgerThreads()=" + purgerThreads() + ", fetchPersistentState()=" + fetchPersistentState()
            + ", ignoreModifications()=" + ignoreModifications() + ", properties()=" + properties() + "]";
   }
}
