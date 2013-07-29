package org.infinispan.loaders.cassandra;

import org.infinispan.loaders.CacheLoaderException;
import org.infinispan.loaders.cassandra.configuration.CassandraCacheStoreConfiguration;
import org.infinispan.loaders.cassandra.configuration.CassandraCacheStoreConfigurationBuilder;
import org.infinispan.loaders.keymappers.DefaultTwoWayKey2StringMapper;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author navssurtani
 * @since 6.0
 */
@Test(groups = "unit", testName = "loaders.cassandra.CassandraCacheStoreConfigurationTest")
public class CassandraCacheStoreConfigurationTest {

   public void setConfigurationPropertiesFileTest() throws CacheLoaderException {
      CassandraCacheStoreConfigurationBuilder storeBuilder = TestCacheManagerFactory
            .getDefaultCacheConfiguration(false)
            .loaders()
               .addLoader(CassandraCacheStoreConfigurationBuilder.class)
               .purgeSynchronously(true);

      CassandraCacheStoreConfiguration storeConfiguration = storeBuilder.create();


      // Test some default configuration settings.
      Assert.assertEquals("InfinispanEntries", storeConfiguration.entryColumnFamily());
      Assert.assertEquals("InfinispanExpiration", storeConfiguration.expirationColumnFamily());
      Assert.assertEquals("Infinispan", storeConfiguration.keySpace());
      Assert.assertEquals(DefaultTwoWayKey2StringMapper.class.getName(), storeConfiguration.keyMapper());
      Assert.assertTrue(storeConfiguration.autoCreateKeyspace());
      Assert.assertTrue(storeConfiguration.framed());
      Assert.assertFalse(storeConfiguration.sharedKeyspace());
   }
}
