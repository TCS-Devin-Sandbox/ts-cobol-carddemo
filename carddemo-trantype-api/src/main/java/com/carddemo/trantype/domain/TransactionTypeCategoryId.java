package com.carddemo.trantype.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TransactionTypeCategoryId implements Serializable {

    @Column(name = "trc_type_code", length = 2, columnDefinition = "bpchar(2)")
    private String typeCode;

    @Column(name = "trc_type_category", length = 4, columnDefinition = "bpchar(4)")
    private String category;

    protected TransactionTypeCategoryId() {
    }

    public TransactionTypeCategoryId(String typeCode, String category) {
        this.typeCode = typeCode;
        this.category = category;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TransactionTypeCategoryId other
                && Objects.equals(typeCode, other.typeCode)
                && Objects.equals(category, other.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeCode, category);
    }
}
