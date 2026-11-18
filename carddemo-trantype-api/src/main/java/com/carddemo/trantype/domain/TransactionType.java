package com.carddemo.trantype.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** CARDDEMO.TRANSACTION_TYPE (DCLTRTYP). */
@Entity
@Table(name = "transaction_type")
public class TransactionType {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tr_type", length = 2, nullable = false)
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
