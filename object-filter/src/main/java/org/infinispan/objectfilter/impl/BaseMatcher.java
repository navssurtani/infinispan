package org.infinispan.objectfilter.impl;

import org.hibernate.hql.QueryParser;
import org.infinispan.objectfilter.FilterCallback;
import org.infinispan.objectfilter.FilterSubscription;
import org.infinispan.objectfilter.Matcher;
import org.infinispan.objectfilter.ObjectFilter;
import org.infinispan.objectfilter.impl.hql.FilterParsingResult;
import org.infinispan.objectfilter.impl.hql.FilterProcessingChain;
import org.infinispan.objectfilter.impl.predicateindex.MatcherEvalContext;
import org.infinispan.objectfilter.impl.syntax.BooleanExpr;
import org.infinispan.objectfilter.impl.syntax.BooleanFilterNormalizer;
import org.infinispan.objectfilter.query.FilterQuery;
import org.infinispan.objectfilter.query.FilterQueryFactory;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author anistor@redhat.com
 * @since 7.0
 */
abstract class BaseMatcher<TypeMetadata, AttributeMetadata, AttributeId extends Comparable<AttributeId>> implements Matcher {

   private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

   private final Lock read = readWriteLock.readLock();

   private final Lock write = readWriteLock.writeLock();

   private final QueryParser queryParser = new QueryParser();

   private final BooleanFilterNormalizer booleanFilterNormalizer = new BooleanFilterNormalizer();

   protected final Map<String, FilterRegistry<TypeMetadata, AttributeMetadata, AttributeId>> filtersByTypeName = new HashMap<String, FilterRegistry<TypeMetadata, AttributeMetadata, AttributeId>>();

   protected final Map<TypeMetadata, FilterRegistry<TypeMetadata, AttributeMetadata, AttributeId>> filtersByType = new HashMap<TypeMetadata, FilterRegistry<TypeMetadata, AttributeMetadata, AttributeId>>();

   private FilterQueryFactory queryFactory;

   @Override
   public QueryFactory<Query> getQueryFactory() {
      if (queryFactory == null) {
         queryFactory = new FilterQueryFactory();
      }
      return queryFactory;
   }

   /**
    * Executes the registered filters and notifies each one of them whether it was satisfied or not by the given
    * instance.
    *
    * @param instance the instance to match filters against
    */
   @Override
   public void match(Object instance) {
      if (instance == null) {
         throw new IllegalArgumentException("argument cannot be null");
      }

      read.lock();
      try {
         MatcherEvalContext<TypeMetadata, AttributeMetadata, AttributeId> ctx = startContext(instance);
         if (ctx != null) {
            ctx.match();
         }
      } finally {
         read.unlock();
      }
   }

   @Override
   public ObjectFilter getObjectFilter(Query query) {
      if (!(query instanceof FilterQuery)) {
         throw new IllegalArgumentException("The Query object must be created by a QueryFactory returned by this Matcher");
      }
      FilterQuery filterQuery = (FilterQuery) query;
      return getObjectFilter(filterQuery.getJpqlString());
   }

   @Override
   public ObjectFilter getObjectFilter(String jpaQuery) {
      FilterParsingResult<TypeMetadata> parsingResult = parse(jpaQuery);
      BooleanExpr normalizedFilter = booleanFilterNormalizer.normalize(parsingResult.getQuery());
      return new ObjectFilterImpl<TypeMetadata, AttributeMetadata, AttributeId>(this, createMetadataAdapter(parsingResult.getTargetEntityMetadata()), jpaQuery, parsingResult, normalizedFilter);
   }

   @Override
   public ObjectFilter getObjectFilter(FilterSubscription filterSubscription) {
      FilterSubscriptionImpl<TypeMetadata, AttributeMetadata, AttributeId> filterSubscriptionImpl = (FilterSubscriptionImpl<TypeMetadata, AttributeMetadata, AttributeId>) filterSubscription;
      return getObjectFilter(filterSubscriptionImpl.getQueryString());
   }

   @Override
   public FilterSubscription registerFilter(Query query, FilterCallback callback) {
      if (!(query instanceof FilterQuery)) {
         throw new IllegalArgumentException("The Query object must be created by a QueryFactory returned by this Matcher");
      }
      FilterQuery filterQuery = (FilterQuery) query;
      return registerFilter(filterQuery.getJpqlString(), callback);
   }

   @Override
   public FilterSubscription registerFilter(String jpaQuery, FilterCallback callback) {
      FilterParsingResult<TypeMetadata> parsingResult = parse(jpaQuery);
      BooleanExpr normalizedFilter = booleanFilterNormalizer.normalize(parsingResult.getQuery());

      write.lock();
      try {
         FilterRegistry<TypeMetadata, AttributeMetadata, AttributeId> filterRegistry = filtersByTypeName.get(parsingResult.getTargetEntityName());
         if (filterRegistry == null) {
            filterRegistry = new FilterRegistry<TypeMetadata, AttributeMetadata, AttributeId>(createMetadataAdapter(parsingResult.getTargetEntityMetadata()), true);
            filtersByTypeName.put(parsingResult.getTargetEntityName(), filterRegistry);
            filtersByType.put(filterRegistry.getMetadataAdapter().getTypeMetadata(), filterRegistry);
         }
         return filterRegistry.addFilter(jpaQuery, normalizedFilter, parsingResult.getProjections(), parsingResult.getSortFields(), callback);
      } finally {
         write.unlock();
      }
   }

   private FilterParsingResult<TypeMetadata> parse(String jpaQuery) {
      //todo [anistor] query params not yet fully supported by HQL parser. to be added later.
      return queryParser.parseQuery(jpaQuery, createFilterProcessingChain(null));
   }

   @Override
   public void unregisterFilter(FilterSubscription filterSubscription) {
      FilterSubscriptionImpl filterSubscriptionImpl = (FilterSubscriptionImpl) filterSubscription;
      write.lock();
      try {
         FilterRegistry<TypeMetadata, AttributeMetadata, AttributeId> filterRegistry = filtersByTypeName.get(filterSubscriptionImpl.getEntityTypeName());
         if (filterRegistry != null) {
            filterRegistry.removeFilter(filterSubscription);
         } else {
            throw new IllegalStateException("Reached illegal state");
         }
         if (filterRegistry.getNumFilters() == 0) {
            filtersByTypeName.remove(filterRegistry.getMetadataAdapter().getTypeName());
            filtersByType.remove(filterRegistry.getMetadataAdapter().getTypeMetadata());
         }
      } finally {
         write.unlock();
      }
   }

   /**
    * Creates a new MatcherEvalContext only if the given instance is of a type that has some filters registered. This
    * method is called while holding the internal write lock.
    *
    * @param instance the instance to filter; never null
    * @return the context or null if no filter was registered for the instance
    */
   protected abstract MatcherEvalContext<TypeMetadata, AttributeMetadata, AttributeId> startContext(Object instance);

   protected abstract MatcherEvalContext<TypeMetadata, AttributeMetadata, AttributeId> startContext(Object instance, FilterSubscriptionImpl<TypeMetadata, AttributeMetadata, AttributeId> filterSubscription);

   protected abstract MatcherEvalContext<TypeMetadata, AttributeMetadata, AttributeId> createContext(Object instance);

   protected abstract FilterProcessingChain<TypeMetadata> createFilterProcessingChain(Map<String, Object> namedParameters);

   protected abstract MetadataAdapter<TypeMetadata, AttributeMetadata, AttributeId> createMetadataAdapter(TypeMetadata typeMetadata);

   protected abstract FilterRegistry<TypeMetadata, AttributeMetadata, AttributeId> getFilterRegistryForType(TypeMetadata entityType);
}