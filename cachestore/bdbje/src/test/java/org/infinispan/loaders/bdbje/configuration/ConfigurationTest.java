package org.infinispan.loaders.bdbje.configuration;

import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.testng.annotations.Test;

@Test(groups = "unit", testName = "loaders.bdbje.configuration.ConfigurationTest")
public class ConfigurationTest {

   public void testBdbjeCacheStoreConfigurationAdaptor() {
      ConfigurationBuilder b = new ConfigurationBuilder();
      b.loaders().addStore(BdbjeCacheStoreConfigurationBuilder.class).location("/tmp/bdbje").cacheDbNamePrefix("myprefix").catalogDbName("mycatalog").fetchPersistentState(true).async().enable();
      Configuration configuration = b.build();
      BdbjeCacheStoreConfiguration store = (BdbjeCacheStoreConfiguration) configuration.loaders().cacheLoaders().get(0);
      assert store.location().equals("/tmp/bdbje");
      assert store.cacheDbNamePrefix().equals("myprefix");
      assert store.catalogDbName().equals("mycatalog");
      assert store.fetchPersistentState();
      assert store.async().enabled();

      b = new ConfigurationBuilder();
      b.loaders().addStore(BdbjeCacheStoreConfigurationBuilder.class).read(store);
      Configuration configuration2 = b.build();
      BdbjeCacheStoreConfiguration store2 = (BdbjeCacheStoreConfiguration) configuration2.loaders().cacheLoaders().get(0);
      assert store2.location().equals("/tmp/bdbje");
      assert store2.cacheDbNamePrefix().equals("myprefix");
      assert store2.catalogDbName().equals("mycatalog");
      assert store2.fetchPersistentState();
      assert store2.async().enabled();
   }
}
