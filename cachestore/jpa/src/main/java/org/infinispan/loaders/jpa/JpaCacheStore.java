package org.infinispan.loaders.jpa;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.GeneratedValue;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.Query;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.IdentifiableType;
import javax.persistence.metamodel.ManagedType;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.Type;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.container.entries.ImmortalCacheEntry;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.loaders.CacheLoaderException;
import org.infinispan.loaders.LockSupportCacheStore;
import org.infinispan.commons.marshall.StreamingMarshaller;
import org.infinispan.commons.util.InfinispanCollections;
import org.infinispan.loaders.jpa.configuration.JpaCacheStoreConfiguration;

/**
 *
 * @author <a href="mailto:rtsang@redhat.com">Ray Tsang</a>
 *
 */
public class JpaCacheStore<T extends JpaCacheStoreConfiguration> extends LockSupportCacheStore<Integer, T> {
	private AdvancedCache<?, ?> cache;
	private EntityManagerFactory emf;
	private EntityManagerFactoryRegistry emfRegistry;

	private final static byte BINARY_STREAM_DELIMITER = 100;

	@Override
	public void init(T configuration, Cache<?, ?> cache,
			StreamingMarshaller m) throws CacheLoaderException {
		super.init(configuration, cache, m);
		this.cache = cache.getAdvancedCache();
		this.emfRegistry = this.cache.getComponentRegistry().getGlobalComponentRegistry().getComponent(EntityManagerFactoryRegistry.class);
	}

	@Override
	public void start() throws CacheLoaderException {
		super.start();

		try {
			this.emf = this.emfRegistry.getEntityManagerFactory(configuration.persistenceUnitName());
		} catch (PersistenceException e) {
			throw new JpaCacheLoaderException("Persistence Unit [" + configuration.persistenceUnitName() + "] not found", e);
		}

		ManagedType<?> mt;

		try {
			mt = emf.getMetamodel()
				.entity(configuration.entityClass());
		} catch (IllegalArgumentException e) {
			throw new JpaCacheLoaderException("Entity class [" + configuration.entityClass().getName() + " specified in " +
               "configuration is not recognized by the EntityManagerFactory with Persistence Unit [" + this
               .configuration.persistenceUnitName() + "]", e);
		}

		if (!(mt instanceof IdentifiableType)) {
			throw new JpaCacheLoaderException(
					"Entity class must have one and only one identifier (@Id or @EmbeddedId)");
		}
		IdentifiableType<?> it = (IdentifiableType<?>) mt;
		if (!it.hasSingleIdAttribute()) {
			throw new JpaCacheLoaderException(
					"Entity class has more than one identifier.  It must have only one identifier.");
		}

		Type<?> idType = it.getIdType();
		Class<?> idJavaType = idType.getJavaType();

		if (idJavaType.isAnnotationPresent(GeneratedValue.class)) {
			throw new JpaCacheLoaderException(
					"Entity class has one identifier, but it must not have @GeneratedValue annotation");
		}

	}

	public EntityManagerFactory getEntityManagerFactory() {
		return emf;
	}

	@Override
	public void stop() throws CacheLoaderException {
		try {
		   this.emfRegistry.closeEntityManagerFactory(configuration.persistenceUnitName());
			super.stop();
		} catch (Throwable t) {
			throw new CacheLoaderException(
					"Exceptions occurred while stopping store", t);
		}
	}

	protected boolean isValidKeyType(Object key) {
		return emf.getMetamodel().entity(configuration.entityClass()).getIdType().getJavaType().isAssignableFrom(key.getClass());
	}

	@Override
	protected void clearLockSafe() throws CacheLoaderException {
		EntityManager em = emf.createEntityManager();
		EntityTransaction txn = em.getTransaction();

		try {
			txn.begin();

			String name = em.getMetamodel().entity(configuration.entityClass())
					.getName();
			Query query = em.createQuery("DELETE FROM " + name);
			query.executeUpdate();

			txn.commit();
		} catch (Exception e) {
			if (txn != null && txn.isActive())
				txn.rollback();
			throw new CacheLoaderException("Exception caught in clear()", e);
		} finally {
			em.close();
		}

	}

	@Override
	protected Set<InternalCacheEntry> loadAllLockSafe()
			throws CacheLoaderException {
		return loadLockSafe(-1);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected Set<InternalCacheEntry> loadLockSafe(int maxEntries)
			throws CacheLoaderException {

	   if (maxEntries == 0)
	      return InfinispanCollections.emptySet();

		EntityManager em = emf.createEntityManager();

		try {
			CriteriaBuilder cb = em.getCriteriaBuilder();
			CriteriaQuery cq = cb.createQuery(configuration.entityClass());
			cq.select(cq.from(configuration.entityClass()));

			TypedQuery q = em.createQuery(cq);
			if (maxEntries > 0)
			   q.setMaxResults(maxEntries);

			List list = q.getResultList();

			if (list == null || list.isEmpty()) {
				return Collections.emptySet();
			}

			PersistenceUnitUtil util = emf.getPersistenceUnitUtil();

			Set<InternalCacheEntry> result = new HashSet<InternalCacheEntry>(
					list.size());
			for (Object o : list) {
				Object key = util.getIdentifier(o);
				result.add(new ImmortalCacheEntry(key, o));
			}

			return result;
		} finally {
			em.close();
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected Set<Object> loadAllKeysLockSafe(Set<Object> keysToExclude)
			throws CacheLoaderException {

		EntityManager em = emf.createEntityManager();

		try {
			CriteriaBuilder cb = em.getCriteriaBuilder();
			CriteriaQuery<Tuple> cq = cb.createTupleQuery();

			Root root = cq.from(configuration.entityClass());
			Type idType = root.getModel().getIdType();
			SingularAttribute idAttr = root.getModel().getId(
					idType.getJavaType());

			cq.multiselect(root.get(idAttr));
			List<Tuple> tuples = em.createQuery(cq).getResultList();

			Set<Object> keys = new HashSet<Object>();
			for (Tuple t : tuples) {
				Object id = t.get(0);
				if (includeKey(id, keysToExclude)) {
					keys.add(id);
				}
			}

			return keys;
		} finally {
			em.close();
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected void toStreamLockSafe(ObjectOutput oos)
			throws CacheLoaderException {

		EntityManager em = emf.createEntityManager();

		try {
			CriteriaBuilder cb = em.getCriteriaBuilder();
			CriteriaQuery cq = cb.createQuery(configuration.entityClass());
			cq.select(cq.from(configuration.entityClass()));

			TypedQuery q = em.createQuery(cq);

			Iterator it = q.getResultList().iterator();
			for (; it.hasNext();) {
				Object o = it.next();
				marshaller.objectToObjectStream(o, oos);
			}
			marshaller.objectToObjectStream(BINARY_STREAM_DELIMITER, oos);
		} catch (IOException e) {
			throw new CacheLoaderException("IO Exception in toStreamLockSafe",
					e);
		} finally {
			em.close();
		}
	}

	@Override
	protected void fromStreamLockSafe(ObjectInput ois)
			throws CacheLoaderException {

		long batchSize = 0;

		EntityManager em = emf.createEntityManager();
		EntityTransaction txn = em.getTransaction();
		try {
			Object o = marshaller.objectFromObjectStream(ois);

			txn.begin();
			while (o != null) {
				if (!o.getClass().isAnnotationPresent(Entity.class))
					break;

				em.merge(o);
				batchSize++;

				if (batchSize >= configuration.batchSize()) {
					em.flush();
					em.clear();
					batchSize = 0;
				}

				o = marshaller.objectFromObjectStream(ois);
			}

			txn.commit();
		} catch (InterruptedException e) {
			if (txn != null && txn.isActive())
				txn.rollback();

			Thread.currentThread().interrupt();
		} catch (Exception e) {
			if (txn != null && txn.isActive())
				txn.rollback();

			throw new CacheLoaderException(e);
		} finally {
			em.close();
		}
	}

	@Override
	protected boolean removeLockSafe(Object key, Integer lockingKey)
			throws CacheLoaderException {

		if (!isValidKeyType(key)) {
			return false;
		}

		EntityManager em = emf.createEntityManager();
		try {
			Object o = em.find(configuration.entityClass(), key);
			if (o == null) {
				return false;
			}

			EntityTransaction txn = em.getTransaction();
			try {
				txn.begin();
				em.remove(o);
				txn.commit();

				return true;
			} catch (Exception e) {
				if (txn != null && txn.isActive())
					txn.rollback();
				throw new CacheLoaderException(
						"Exception caught in removeLockSafe()", e);
			}
		} finally {
			em.close();
		}
	}

	@Override
	protected void storeLockSafe(InternalCacheEntry entry, Integer lockingKey)
			throws CacheLoaderException {

		EntityManager em = emf.createEntityManager();

		Object o = entry.getValue();
		try {
			if (!configuration.entityClass().isAssignableFrom(o.getClass())) {
				throw new JpaCacheLoaderException(
						"This cache is configured with JPA CacheStore to only store values of type " + configuration
                        .entityClass().getName());
			} else {
				EntityTransaction txn = em.getTransaction();
				Object id = emf.getPersistenceUnitUtil().getIdentifier(o);
				if (!entry.getKey().equals(id)) {
					throw new JpaCacheLoaderException(
							"Entity id value must equal to key of cache entry: "
									+ "key = [" + entry.getKey() + "], id = ["
									+ id + "]");
				}
				try {
					txn.begin();

					em.merge(o);

					txn.commit();
				} catch (Exception e) {
					if (txn != null && txn.isActive())
						txn.rollback();
					throw new CacheLoaderException(
							"Exception caught in store()", e);
				}
			}
		} finally {
			em.close();
		}

	}

	@Override
	protected InternalCacheEntry loadLockSafe(Object key, Integer lockingKey)
			throws CacheLoaderException {

		if (!isValidKeyType(key)) {
			return null;
		}

		EntityManager em = emf.createEntityManager();
		try {
			Object o = em.find(configuration.entityClass(), key);
			if (o == null)
				return null;

			return new ImmortalCacheEntry(key, o);
		} finally {
			em.close();
		}

	}

	@Override
	protected Integer getLockFromKey(Object key) throws CacheLoaderException {
		return key.hashCode() & 0xfffffc00;
	}

	protected boolean includeKey(Object key, Set<Object> keysToExclude) {
		return keysToExclude == null || !keysToExclude.contains(key);
	}

	@Override
	protected void purgeInternal() throws CacheLoaderException {
		// Immortal - no purging needed
	}

}
