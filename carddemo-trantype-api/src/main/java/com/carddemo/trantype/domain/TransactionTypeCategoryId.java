package com.carddemo.trantype.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TransactionTypeCategoryId implements Serializable {

    @Column(name = "trc_type_code", length = 2, columnDefinition = "char(2)")
    private String trcTypeCode;

    @Column(name = "trc_type_category", length = 4, columnDefinition = "char(4)")
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
        return o instanceof TransactionTypeCategoryId other
                && Objects.equals(trcTypeCode, other.trcTypeCode)
                && Objects.equals(trcTypeCategory, other.trcTypeCategory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trcTypeCode, trcTypeCategory);
    }
}
