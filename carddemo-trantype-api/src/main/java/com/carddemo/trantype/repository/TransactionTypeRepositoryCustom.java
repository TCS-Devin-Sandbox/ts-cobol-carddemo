package com.carddemo.trantype.repository;

import com.carddemo.trantype.domain.TransactionType;
import java.util.List;
import java.util.Optional;

public interface TransactionTypeRepositoryCustom {

    /** C-TR-TYPE-FORWARD: tr_type &gt;= key ORDER BY tr_type. */
    List<TransactionType> findForward(String key, TypeFilters filters, int limit);

    /** C-TR-TYPE-BACKWARD: tr_type &lt; key ORDER BY tr_type DESC. */
    List<TransactionType> findBackward(String key, TypeFilters filters, int limit);

    boolean existsBefore(String key, TypeFilters filters);

    Optional<String> firstKeyAfter(String key, TypeFilters filters);

    /** 9100-CHECK-FILTERS: SELECT COUNT(1) with the filter predicates. */
    long countMatching(TypeFilters filters);

    /** Plain INSERT (CTTU 9700-INSERT-RECORD); duplicate keys surface as DataIntegrityViolationException. */
    void insert(TransactionType transactionType);
}
