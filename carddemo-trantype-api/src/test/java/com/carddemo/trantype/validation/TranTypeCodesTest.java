package com.carddemo.trantype.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TranTypeCodesTest {

    @ParameterizedTest(name = "[{0}] -> {1}")
    @CsvSource(delimiter = '|', value = {
            "' '|Tran Type code must be supplied.",
            "*|Tran Type code must be supplied.",
            "ab|Tran Type code must be numeric.",
            "1a|Tran Type code must be numeric.",
            "123|Tran Type code must be numeric.",
            "0|Tran Type code must not be zero.",
            "00|Tran Type code must not be zero."
    })
    void rejectsWithSourceLiteral(String input, String expected) {
        assertThat(TranTypeCodes.validate(input)).isEqualTo(expected);
    }

    @Test
    void nullIsRequired() {
        assertThat(TranTypeCodes.validate(null)).isEqualTo("Tran Type code must be supplied.");
    }

    @ParameterizedTest
    @CsvSource({"5,05", "05,05", "' 7',07", "42,42"})
    void validCodesAreZeroPadded(String input, String expected) {
        assertThat(TranTypeCodes.validate(input)).isNull();
        assertThat(TranTypeCodes.normalise(input)).isEqualTo(expected);
    }

    @Test
    void normaliseIgnoresDefaultLocale() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"));
            assertThat(TranTypeCodes.normalise("5")).isEqualTo("05");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }
}
