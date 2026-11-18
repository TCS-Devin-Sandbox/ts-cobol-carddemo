package com.carddemo.trantype.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** CARDDEMO.TRANSACTION_TYPE (DCLGEN DCLTRTYP). */
@Entity
@Table(name = "transaction_type")
public class TransactionType {

    @Id
    @Column(name = "tr_type", length = 2, columnDefinition = "bpchar(2)")
    private String trType;

    @Column(name = "tr_description", length = 50, nullable = false)
    private String description;

    protected TransactionType() {
    }

    public TransactionType(String trType, String description) {
        this.trType = trType;
        this.description = description;
    }

    public String getTrType() {
        return trType;
    }

    public String getDescription() {
        return description;
    }
}
