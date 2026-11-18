package com.carddemo.trantype.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TranTypeRulesTest {

    @ParameterizedTest(name = "[{index}] type ''{0}''")
    @CsvSource(value = {
            "NULL|Tran Type code must be supplied.",
            "'  '|Tran Type code must be supplied.",
            "AB|Tran Type code must be numeric.",
            "1A|Tran Type code must be numeric.",
            "123|Tran Type code must be numeric.",
            "00|Tran Type code must not be zero.",
            "0|Tran Type code must not be zero."}, delimiter = '|', nullValues = "NULL")
    void typeCodeErrors(String input, String expected) {
        assertThat(TranTypeRules.typeCodeError(input)).contains(expected);
        assertThatThrownBy(() -> TranTypeRules.normalizeTypeCode(input)).hasMessage(expected);
    }

    @ParameterizedTest
    @CsvSource({"1,01", "01,01", "' 7',07", "99,99"})
    void typeCodeIsZeroPadded(String input, String expected) {
        assertThat(TranTypeRules.normalizeTypeCode(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] desc ''{0}''")
    @CsvSource(value = {
            "NULL|Transaction Desc must be supplied.",
            "'   '|Transaction Desc must be supplied.",
            "BAD-DESC|Transaction Desc can have numbers or alphabets only.",
            "CASH $|Transaction Desc can have numbers or alphabets only."}, delimiter = '|', nullValues = "NULL")
    void descriptionErrors(String input, String expected) {
        assertThat(TranTypeRules.descriptionError(input)).contains(expected);
    }

    @Test
    void descriptionAcceptsAlphanumericAndSpacesUpTo50() {
        assertThat(TranTypeRules.descriptionError("Cash Advance 2")).isEmpty();
        assertThat(TranTypeRules.normalizeDescription("  PURCHASE  ")).isEqualTo("PURCHASE");
        assertThat(TranTypeRules.descriptionError("A".repeat(50))).isEmpty();
        assertThat(TranTypeRules.descriptionError("A".repeat(51))).contains(SourceMessages.DESC_TOO_LONG);
    }

    @Test
    void typeFilterRules() {
        assertThat(TranTypeRules.typeFilter(null)).isNull();
        assertThat(TranTypeRules.typeFilter(" ")).isNull();
        assertThat(TranTypeRules.typeFilter("00")).isNull();
        assertThat(TranTypeRules.typeFilter("3")).isEqualTo("03");
        assertThatThrownBy(() -> TranTypeRules.typeFilter("X1"))
                .hasMessage("TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER");
    }

    @Test
    void descFilterIsContains() {
        assertThat(TranTypeRules.descFilter(" PAY ")).isEqualTo("%PAY%");
        assertThat(TranTypeRules.descFilter("")).isNull();
    }

    @Test
    void sameDescriptionIgnoresCaseAndTrailingBlanks() {
        assertThat(TranTypeRules.sameDescription("PURCHASE", " purchase ")).isTrue();
        assertThat(TranTypeRules.sameDescription("PURCHASE", "PURCHASES")).isFalse();
    }
}
