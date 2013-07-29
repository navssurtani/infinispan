package org.infinispan.loaders.leveldb;

import org.infinispan.loaders.CacheLoaderException;
import org.infinispan.loaders.leveldb.configuration.ImplementationType;
import org.infinispan.loaders.leveldb.configuration.LevelDBCacheStoreConfigurationBuilder;
import org.testng.annotations.Test;

@Test(groups = "unit", testName = "loaders.leveldb.JniLevelDBCacheStoreTest")
public class JniLevelDBCacheStoreTest extends LevelDBCacheStoreTest {

   @Override
	protected LevelDBCacheStoreConfigurationBuilder createBuilder() throws CacheLoaderException {
      LevelDBCacheStoreConfigurationBuilder storeConfigurationBuilder = super.createBuilder();
      storeConfigurationBuilder.implementationType(ImplementationType.JNI);
      return storeConfigurationBuilder;
   }
}
