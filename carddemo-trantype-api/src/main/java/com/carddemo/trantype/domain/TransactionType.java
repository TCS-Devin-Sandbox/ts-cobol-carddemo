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
    @Column(name = "tr_type", length = 2, columnDefinition = "char(2)")
    private String trType;

    @Column(name = "tr_description", length = 50, nullable = false)
    private String trDescription;

    protected TransactionType() {
    }

    public TransactionType(String trType, String trDescription) {
        this.trType = trType;
        this.trDescription = trDescription;
    }

    public String getTrType() {
        return trType;
    }

    public String getTrDescription() {
        return trDescription;
    }

    public void setTrDescription(String trDescription) {
        this.trDescription = trDescription;
    }
}
