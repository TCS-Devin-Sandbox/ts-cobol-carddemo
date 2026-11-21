package com.carddemo.trantype.repository;

import com.carddemo.trantype.domain.TransactionTypeCategory;
import com.carddemo.trantype.domain.TransactionTypeCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionTypeCategoryRepository
        extends JpaRepository<TransactionTypeCategory, TransactionTypeCategoryId> {

    long countByIdTrcTypeCode(String trcTypeCode);
}
