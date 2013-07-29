package org.infinispan.loaders.cassandra;

import java.io.IOException;

import org.apache.cassandra.config.ConfigurationException;
import org.apache.thrift.transport.TTransportException;
import org.infinispan.loaders.BaseCacheStoreTest;
import org.infinispan.loaders.CacheLoaderException;
import org.infinispan.loaders.CacheStore;
import org.infinispan.loaders.cassandra.configuration.CassandraCacheStoreConfigurationBuilder;
import org.infinispan.loaders.cassandra.configuration.CassandraCacheStoreConfigurationChildBuilder;
import org.infinispan.loaders.keymappers.UnsupportedKeyTypeException;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Test(groups = "unit", testName = "loaders.cassandra.CassandraCacheStoreTest")
public class CassandraCacheStoreTest extends BaseCacheStoreTest {
   private static EmbeddedServerHelper embedded;

   /**
    * Set embedded cassandra up and spawn it in a new thread.
    *
    * @throws TTransportException
    * @throws IOException
    * @throws InterruptedException
    * @throws ConfigurationException
    */
   @BeforeClass
   public static void setup() throws TTransportException, IOException, InterruptedException,
            ConfigurationException {
      embedded = new EmbeddedServerHelper();
      embedded.setup();
   }

   @AfterClass
   public static void cleanup() throws IOException {
      EmbeddedServerHelper.teardown();
      embedded = null;
   }

   @Override
   protected CacheStore createCacheStore() throws Exception {
      CassandraCacheStore cs = new CassandraCacheStore();
      CassandraCacheStoreConfigurationBuilder storeBuilder = TestCacheManagerFactory
            .getDefaultCacheConfiguration(false)
            .loaders()
               .addLoader(CassandraCacheStoreConfigurationBuilder.class)
               .purgeSynchronously(true)
               .addServer()
                  .host("127.0.0.1")
               .autoCreateKeyspace(true)
               .keySpace("Infinispan");
      cs.init(storeBuilder.create(), getCache(), getMarshaller());
      cs.start();
      return cs;
   }

   @Override
   @Test(expectedExceptions = UnsupportedKeyTypeException.class)
   public void testLoadAndStoreMarshalledValues() throws CacheLoaderException {
      super.testLoadAndStoreMarshalledValues();
   }

}
