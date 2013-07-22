package org.infinispan.configuration.cache;

import org.infinispan.commons.configuration.BuiltBy;
import org.infinispan.commons.util.TypedProperties;

/**
 * ClusterCacheLoaderConfiguration.
 *
 * @author Tristan Tarrant
 * @since 5.2
 */
@BuiltBy(ClusterCacheLoaderConfigurationBuilder.class)
public class ClusterCacheLoaderConfiguration extends AbstractLoaderConfiguration {
   private final long remoteCallTimeout;

   ClusterCacheLoaderConfiguration(long remoteCallTimeout, TypedProperties properties) {
      super(properties);
      this.remoteCallTimeout = remoteCallTimeout;
   }

   public long remoteCallTimeout() {
      return remoteCallTimeout;
   }

   @Override
   public String toString() {
      return "ClusterCacheLoaderConfiguration [remoteCallTimeout=" + remoteCallTimeout + "]";
   }

}
