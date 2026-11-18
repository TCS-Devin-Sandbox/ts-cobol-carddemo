package com.carddemo.trantype.repository;

import com.carddemo.trantype.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionTypeRepository
        extends JpaRepository<TransactionType, String>, TransactionTypeRepositoryCustom {
}
