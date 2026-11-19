package com.carddemo.trantype.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/** CARDDEMO.TRANSACTION_TYPE_CATEGORY (DCLTRCAT); FK to TRANSACTION_TYPE ON DELETE RESTRICT. */
@Entity
@Table(name = "transaction_type_category")
public class TransactionTypeCategory {

    @EmbeddedId
    private TransactionTypeCategoryId id;

    @MapsId("trcTypeCode")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trc_type_code", referencedColumnName = "tr_type")
    private TransactionType transactionType;

    @Column(name = "trc_cat_data", length = 50, nullable = false)
    private String trcCatData;

    protected TransactionTypeCategory() {
    }

    public TransactionTypeCategory(TransactionType transactionType, String category, String trcCatData) {
        this.id = new TransactionTypeCategoryId(transactionType.getTrType(), category);
        this.transactionType = transactionType;
        this.trcCatData = trcCatData;
    }

    public TransactionTypeCategoryId getId() {
        return id;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public String getTrcCatData() {
        return trcCatData;
    }
}
