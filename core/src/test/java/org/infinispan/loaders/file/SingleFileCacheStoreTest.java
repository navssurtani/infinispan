package org.infinispan.loaders.file;

import org.infinispan.configuration.cache.SingleFileCacheStoreConfiguration;
import org.infinispan.configuration.cache.SingleFileCacheStoreConfigurationBuilder;
import org.infinispan.loaders.BaseCacheStoreTest;
import org.infinispan.loaders.CacheLoaderException;
import org.infinispan.loaders.CacheStore;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.infinispan.test.TestingUtil.recursiveFileRemove;

/**
 * Low level single-file cache store tests.
 *
 * @author Galder Zamarreño
 * @since 6.0
 */
@Test(groups = "unit", testName = "loaders.file.SingleFileCacheStoreTest")
public class SingleFileCacheStoreTest extends BaseCacheStoreTest {

   SingleFileCacheStore store;
   String tmpDirectory;

   @BeforeClass
   protected void setUpTempDir() {
      tmpDirectory = TestingUtil.tmpDirectory(this);
   }

   @AfterClass
   protected void clearTempDir() {
      recursiveFileRemove(tmpDirectory);
   }

   @Override
   protected CacheStore createCacheStore() throws Exception {
      clearTempDir();
      store = new SingleFileCacheStore();
      SingleFileCacheStoreConfiguration fileStoreConfiguration = TestCacheManagerFactory
            .getDefaultCacheConfiguration(false)
            .loaders()
               .addLoader(SingleFileCacheStoreConfigurationBuilder.class)
                  .location(this.tmpDirectory)
                  .purgeSynchronously(true)
                  .create();
      store.init(fileStoreConfiguration, getCache(), getMarshaller());
      store.start();
      return store;
   }

   @Override
   @Test(enabled = false)
   public void testStreamingAPI() throws IOException, CacheLoaderException {
      // streaming API not really used by production code (except by decorators)
   }

   @Override
   @Test(enabled = false)
   public void testStreamingAPIReusingStreams() throws IOException, CacheLoaderException {
      // streaming API not really used by production code (except by decorators)
   }

}
