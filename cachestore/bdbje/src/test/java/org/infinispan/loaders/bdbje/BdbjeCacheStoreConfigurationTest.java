package org.infinispan.loaders.bdbje;

import org.infinispan.loaders.CacheLoaderException;
import org.infinispan.loaders.bdbje.configuration.BdbjeCacheStoreConfigurationBuilder;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit tests that cover {@link  BdbjeCacheStoreConfig }
 *
 * @author Adrian Cole
 * @since 4.0
 */
@Test(groups = "unit", enabled = true, testName = "loaders.bdbje.BdbjeCacheStoreConfigurationTest")
public class BdbjeCacheStoreConfigurationTest {

    private BdbjeCacheStoreConfigurationBuilder storeBuilder;

    @BeforeMethod
    public void setUp() throws Exception {
        storeBuilder = TestCacheManagerFactory.getDefaultCacheConfiguration(false)
              .loaders()
                  .addLoader(BdbjeCacheStoreConfigurationBuilder.class);
    }

    @AfterMethod
    public void tearDown() throws CacheLoaderException {
       storeBuilder = null;
    }

    @Test
    public void testGetMaxTxRetries() {
       Assert.assertEquals(5, storeBuilder.create().maxTxRetries());
    }

    @Test
    public void testSetMaxTxRetries() {
       storeBuilder.maxTxRetries(1);
       Assert.assertEquals(1, storeBuilder.create().maxTxRetries());
    }

    @Test
    public void testGetLockAcquistionTimeout() {
       Assert.assertEquals(60*1000, storeBuilder.create().lockAcquisitionTimeout());
    }

    @Test
    public void testSetLockAcquistionTimeoutMicros() {
       Assert.assertEquals(1, storeBuilder.create().lockAcquisitionTimeout());
    }

    @Test
    public void testGetLocationDefault() {
       Assert.assertEquals("Infinispan-BdbjeCacheStore", storeBuilder.create().location());
    }

    @Test
    public void testSetLocation() {
       storeBuilder.location("foo");
       Assert.assertEquals("foo", storeBuilder.create().location());
    }

    @Test
    public void testSetCacheDb() {
       storeBuilder.cacheDbNamePrefix("foo");
       Assert.assertEquals("foo", storeBuilder.create().cacheDbNamePrefix());
    }

    @Test
    public void testSetCatalogDb() {
       storeBuilder.catalogDbName("foo");
       Assert.assertEquals("foo", storeBuilder.create().catalogDbName());
    }

    @Test
    public void testSetExpiryDb() {
       storeBuilder.expiryDbPrefix("foo");
       Assert.assertEquals("foo", storeBuilder.create().expiryDbPrefix());
    }

}