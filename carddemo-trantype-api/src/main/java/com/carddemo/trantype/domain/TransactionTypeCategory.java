package com.carddemo.trantype.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** CARDDEMO.TRANSACTION_TYPE_CATEGORY (DCLGEN DCLTRCAT); FK ON DELETE RESTRICT to TRANSACTION_TYPE. */
@Entity
@Table(name = "transaction_type_category")
public class TransactionTypeCategory {

    @EmbeddedId
    private TransactionTypeCategoryId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trc_type_code", insertable = false, updatable = false)
    private TransactionType transactionType;

    @Column(name = "trc_cat_data", length = 50, nullable = false)
    private String categoryData;

    protected TransactionTypeCategory() {
    }

    public TransactionTypeCategory(TransactionTypeCategoryId id, String categoryData) {
        this.id = id;
        this.categoryData = categoryData;
    }

    public TransactionTypeCategoryId getId() {
        return id;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public String getCategoryData() {
        return categoryData;
    }
}
