package com.carddemo.trantype.repository;

import com.carddemo.trantype.domain.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

class TransactionTypeRepositoryCustomImpl implements TransactionTypeRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<TransactionType> findForward(String key, TypeFilters filters, int limit) {
        return query("t.trType >= :key", "asc", key, filters, TransactionType.class, "t")
                .setMaxResults(limit).getResultList();
    }

    @Override
    public List<TransactionType> findBackward(String key, TypeFilters filters, int limit) {
        return query("t.trType < :key", "desc", key, filters, TransactionType.class, "t")
                .setMaxResults(limit).getResultList();
    }

    @Override
    public boolean existsBefore(String key, TypeFilters filters) {
        return !query("t.trType < :key", "desc", key, filters, String.class, "t.trType")
                .setMaxResults(1).getResultList().isEmpty();
    }

    @Override
    public Optional<String> firstKeyAfter(String key, TypeFilters filters) {
        return query("t.trType > :key", "asc", key, filters, String.class, "t.trType")
                .setMaxResults(1).getResultList().stream().findFirst();
    }

    @Override
    public long countMatching(TypeFilters filters) {
        StringBuilder jpql = new StringBuilder("select count(t) from TransactionType t where 1 = 1");
        appendFilters(jpql, filters);
        TypedQuery<Long> q = em.createQuery(jpql.toString(), Long.class);
        bindFilters(q, filters);
        return q.getSingleResult();
    }

    @Override
    public void insert(TransactionType transactionType) {
        em.persist(transactionType);
        em.flush();
    }

    private <T> TypedQuery<T> query(String keyPredicate, String order, String key, TypeFilters filters,
                                    Class<T> type, String select) {
        StringBuilder jpql = new StringBuilder("select ").append(select)
                .append(" from TransactionType t where ").append(keyPredicate);
        appendFilters(jpql, filters);
        jpql.append(" order by t.trType ").append(order);
        TypedQuery<T> q = em.createQuery(jpql.toString(), type);
        q.setParameter("key", key);
        bindFilters(q, filters);
        return q;
    }

    private static void appendFilters(StringBuilder jpql, TypeFilters filters) {
        if (filters.typeCode() != null) {
            jpql.append(" and t.trType = :typeCode");
        }
        if (filters.descLike() != null) {
            jpql.append(" and t.trDescription like :descLike");
        }
    }

    private static void bindFilters(TypedQuery<?> q, TypeFilters filters) {
        if (filters.typeCode() != null) {
            q.setParameter("typeCode", filters.typeCode());
        }
        if (filters.descLike() != null) {
            q.setParameter("descLike", filters.descLike());
        }
    }
}
