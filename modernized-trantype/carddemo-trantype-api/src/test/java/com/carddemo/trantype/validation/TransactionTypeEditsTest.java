package com.carddemo.trantype.validation;

import com.carddemo.trantype.Messages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTypeEditsTest {

    @ParameterizedTest(name = "[{index}] blank key \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "*", " * "})
    void typeCodeRequired(String input) { // REQ-CTTU-002, REQ-EDGE-008
        assertThat(TransactionTypeEdits.checkTypeCode(input)).contains(Messages.CODE_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1A", "AB", "-1", "123", "1.0", "+1"})
    void typeCodeNumeric(String input) { // REQ-CTTU-003, REQ-EDGE-011
        assertThat(TransactionTypeEdits.checkTypeCode(input)).contains(Messages.CODE_NUMERIC);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "00", " 0"})
    void typeCodeNotZero(String input) { // REQ-CTTU-004
        assertThat(TransactionTypeEdits.checkTypeCode(input)).contains(Messages.CODE_NOT_ZERO);
    }

    @ParameterizedTest
    @CsvSource({"1,01", "' 5',05", "09,09", "42,42", "'7 ',07"})
    void typeCodeZeroPadded(String input, String expected) { // REQ-CTTU-005, REQ-EDGE-011
        assertThat(TransactionTypeEdits.normalizeTypeCode(input)).isEqualTo(expected);
    }

    @Test
    void normalizeRejectsInvalid() {
        assertThatThrownBy(() -> TransactionTypeEdits.normalizeTypeCode("x"))
                .isInstanceOf(FieldEditException.class)
                .hasMessage(Messages.CODE_NUMERIC);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "*"})
    void descriptionRequired(String input) { // REQ-CTLI-021, REQ-CTTU-010
        assertThat(TransactionTypeEdits.checkDescription(input)).contains(Messages.DESC_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CASH-BACK", "FEE.", "100%", "A_B", "TAB\tX", "ÉCLAIR"})
    void descriptionAlphanumericOnly(String input) { // REQ-EDGE-009
        assertThat(TransactionTypeEdits.checkDescription(input)).contains(Messages.DESC_ALPHANUM);
    }

    @Test
    void descriptionMaxFifty() { // REQ-EDGE-010
        assertThat(TransactionTypeEdits.checkDescription("A".repeat(50))).isEmpty();
        assertThat(TransactionTypeEdits.checkDescription("A".repeat(51))).contains(Messages.DESC_TOO_LONG);
        assertThat(TransactionTypeEdits.checkDescription("  " + "A".repeat(50) + "  ")).isEmpty();
    }

    @Test
    void descriptionTrimmed() { // REQ-EDGE-015
        assertThat(TransactionTypeEdits.normalizeDescription("  Cash Back 2  ")).isEqualTo("Cash Back 2");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "00"})
    void typeFilterNotSupplied(String input) { // REQ-CTLI-009
        assertThat(TransactionTypeEdits.parseTypeFilter(input)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1A", "123", "-1"})
    void typeFilterMustBeTwoDigits(String input) { // REQ-CTLI-010
        assertThatThrownBy(() -> TransactionTypeEdits.parseTypeFilter(input))
                .isInstanceOf(FieldEditException.class)
                .hasMessage(Messages.TYPE_FILTER_INVALID);
    }

    @Test
    void typeFilterValid() {
        assertThat(TransactionTypeEdits.parseTypeFilter("03")).contains("03");
    }

    @Test
    void descriptionFilterBuildsLikePattern() { // REQ-CTLI-011
        assertThat(TransactionTypeEdits.parseDescriptionFilter("  PAY ")).contains("%PAY%");
        assertThat(TransactionTypeEdits.parseDescriptionFilter(" ")).isEmpty();
    }

    @Test
    void sameDescriptionIsCaseInsensitiveAndTrimmed() { // REQ-CTLI-022, REQ-CTTU-011
        assertThat(TransactionTypeEdits.isSameDescription("PURCHASE", " purchase ")).isTrue();
        assertThat(TransactionTypeEdits.isSameDescription("PURCHASE", "PURCHASES")).isFalse();
    }
}
