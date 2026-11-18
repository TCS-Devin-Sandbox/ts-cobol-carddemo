package com.carddemo.trantype.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** CARDDEMO.TRANSACTION_TYPE_CATEGORY (DCLGEN DCLTRCAT); FK to transaction_type ON DELETE RESTRICT. */
@Entity
@Table(name = "transaction_type_category")
public class TransactionTypeCategory {

    @EmbeddedId
    private TransactionTypeCategoryId id;

    @Column(name = "trc_cat_data", length = 50, nullable = false)
    private String trcCatData;

    protected TransactionTypeCategory() {
    }

    public TransactionTypeCategory(TransactionTypeCategoryId id, String trcCatData) {
        this.id = id;
        this.trcCatData = trcCatData;
    }

    public TransactionTypeCategoryId getId() {
        return id;
    }

    public String getTrcCatData() {
        return trcCatData;
    }
}
