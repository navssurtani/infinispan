package org.infinispan.loaders.cloud;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.LoadersConfigurationBuilder;
import org.infinispan.loaders.BaseCacheStoreFunctionalTest;
import org.infinispan.loaders.cloud.configuration.CloudCacheStoreConfigurationBuilder;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;

@Test(groups = "unit", sequential = true, testName = "loaders.cloud.CloudCacheStoreFunctionalIntegrationTest")
public class CloudCacheStoreFunctionalIntegrationTest extends BaseCacheStoreFunctionalTest {

   private String proxyHost;
   private String proxyPort = "-1";
   private int maxConnections = 20;
   private boolean isSecure = false;
   private String csBucket;
   private String accessKey;
   private String secretKey;
   private String cs;

   private static final String sysUsername = System.getProperty("infinispan.test.jclouds.username");
   private static final String sysPassword = System.getProperty("infinispan.test.jclouds.password");
   private static final String sysService = System.getProperty("infinispan.test.jclouds.service");

   @BeforeTest
   @Parameters({"infinispan.test.jclouds.username", "infinispan.test.jclouds.password", "infinispan.test.jclouds.service"})
   protected void setUpClient(@Optional String JcloudsUsername,
                              @Optional String JcloudsPassword,
                              @Optional String JcloudsService) throws Exception {

      accessKey = (JcloudsUsername == null) ? sysUsername : JcloudsUsername;
      secretKey = (JcloudsPassword == null) ? sysPassword : JcloudsPassword;
      cs = (JcloudsService == null) ? sysService : JcloudsService;

      if (accessKey == null || accessKey.trim().length() == 0 || secretKey == null || secretKey.trim().length() == 0) {
         accessKey = "dummy";
         secretKey = "dummy";
      }
      csBucket = (System.getProperty("user.name") + "." + this.getClass().getSimpleName()).toLowerCase().replace('.', '-'); // azure limitation on no periods
      csBucket = csBucket.length() > 32 ? csBucket.substring(0, 32): csBucket;//azure limitation on length
      System.out.printf("accessKey: %1$s, bucket: %2$s%n", accessKey, csBucket);
   }

   @AfterTest
   private void nukeBuckets() throws Exception {
      for (String name: cacheNames) {
         // use JClouds to nuke the buckets
         ConfigurationBuilder builder = TestCacheManagerFactory.getDefaultCacheConfiguration(false);
         CloudCacheStoreConfigurationBuilder cloudBuilder = builder.loaders()
               .addLoader(CloudCacheStoreConfigurationBuilder.class);
         CloudCacheStore ccs = new CloudCacheStore();
         Cache c = mock(Cache.class);
         when(c.getName()).thenReturn(name);
         ccs.init(cloudBuilder.create(), c, null);
         ccs.start();
         System.out.println("**** Nuking container " + ccs.containerName);
         ccs.blobStore.clearContainer(ccs.containerName);
         ccs.blobStore.deleteContainer(ccs.containerName);
         ccs.stop();
      }
      cacheNames.clear();
   }

   @Override
   protected LoadersConfigurationBuilder createCacheStoreConfig(LoadersConfigurationBuilder loaders) {
      throw new UnsupportedOperationException("You don't need to be calling this method on " + this.getClass()
            .getName());
   }
}
