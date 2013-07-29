package org.infinispan.loaders.hbase;

import org.infinispan.loaders.BaseCacheStoreTest;
import org.infinispan.loaders.CacheLoaderException;
import org.infinispan.loaders.CacheStore;
import org.infinispan.loaders.hbase.configuration.HBaseCacheStoreConfigurationBuilder;
import org.infinispan.loaders.hbase.test.HBaseCluster;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "loaders.hbase.HBaseCacheStoreTest")
public class HBaseCacheStoreTest extends BaseCacheStoreTest {

   HBaseCluster hBaseCluster;

   @BeforeClass
   public void beforeClass() throws Exception {
      hBaseCluster = new HBaseCluster();
   }

   @AfterClass
   public void afterClass() throws CacheLoaderException {
      HBaseCluster.shutdown(hBaseCluster);
   }

   @Override
   protected CacheStore createCacheStore() throws Exception {
      HBaseCacheStore cs = new HBaseCacheStore();
      // This uses the default config settings in HBaseCacheStoreConfig
      HBaseCacheStoreConfigurationBuilder storeConfigurationBuilder = TestCacheManagerFactory
            .getDefaultCacheConfiguration(false)
            .loaders()
               .addLoader(HBaseCacheStoreConfigurationBuilder.class)
               .purgeSynchronously(true)
               .hbaseZookeeperClientPort(hBaseCluster.getZooKeeperPort());

      cs.init(storeConfigurationBuilder.create(), getCache(), getMarshaller());
      cs.start();
      return cs;
   }

}
