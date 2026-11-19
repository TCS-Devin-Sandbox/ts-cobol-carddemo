package com.carddemo.trantype.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TransactionTypeCategoryId implements Serializable {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "trc_type_code", length = 2, nullable = false)
    private String trcTypeCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "trc_type_category", length = 4, nullable = false)
    private String trcTypeCategory;

    protected TransactionTypeCategoryId() {
    }

    public TransactionTypeCategoryId(String trcTypeCode, String trcTypeCategory) {
        this.trcTypeCode = trcTypeCode;
        this.trcTypeCategory = trcTypeCategory;
    }

    public String getTrcTypeCode() {
        return trcTypeCode;
    }

    public String getTrcTypeCategory() {
        return trcTypeCategory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionTypeCategoryId other)) {
            return false;
        }
        return Objects.equals(trcTypeCode, other.trcTypeCode)
                && Objects.equals(trcTypeCategory, other.trcTypeCategory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trcTypeCode, trcTypeCategory);
    }
}
