package org.infinispan.configuration.cache;


/**
 * LegacyLoaderAdapter. This interface should disappear in 6.0
 *
 * @author Tristan Tarrant
 * @since 5.2
 */
@Deprecated
public interface LegacyLoaderAdapter {
   T adapt();
}
