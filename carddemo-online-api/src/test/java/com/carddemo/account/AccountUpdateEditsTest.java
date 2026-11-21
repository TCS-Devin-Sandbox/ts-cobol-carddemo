package com.carddemo.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AccountUpdateEditsTest {
    static Map<String, String> valid() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.putAll(Map.of("activeStatus", "Y", "openDate", "2020-01-01", "creditLimit", "9000.00",
                "expirationDate", "2030-01-01", "cashCreditLimit", "300.00", "reissueDate", "2025-03-01",
                "currentBalance", "-1234.56", "currentCycleCredit", "25.01", "currentCycleDebit", "6.50",
                "accountGroupId", "GROUP"));
        fields.putAll(Map.of("firstName", "Example", "middleName", "Q", "lastName", "Fixture",
                "addressLine1", "123 Sample St", "addressLine2", "", "addressLine3", "Sample City",
                "stateCode", "CA", "countryCode", "USA", "zipCode", "90210", "phoneNumber1", "(201)234-5678"));
        fields.putAll(Map.of("phoneNumber2", "", "socialSecurityNumber", "123456789", "governmentIssuedId", "TEST-ID",
                "dateOfBirth", "1980-02-03", "electronicFundsTransferAccountId", "0000000812",
                "primaryCardholderIndicator", "Y", "ficoCreditScore", "750"));
        return fields;
    }

    @Test
    void p1200AcceptsAllFieldsAndOptionalBlankMiddleNameAndPhones() {
        var fields = valid();
        fields.put("middleName", "");
        fields.put("phoneNumber1", "");
        assertThat(new AccountUpdateEdits(fields).validate()).isNull();
    }

    @Test
    void p1200KeepsFirstErrorInSourceEditOrder() {
        var fields = valid();
        fields.put("activeStatus", "X"); fields.put("firstName", ""); fields.put("creditLimit", "bad");
        assertThat(new AccountUpdateEdits(fields).validate()).isEqualTo(
                new AccountUpdateEdits.Invalid("activeStatus", "Account Status must be Y or N."));
        fields.put("activeStatus", "Y");
        assertThat(new AccountUpdateEdits(fields).validate().field()).isEqualTo("creditLimit");
    }

    static Stream<Arguments> paragraphErrors() {
        return Stream.of(
                Arguments.of("1220", "activeStatus", "", "Account Status must be supplied."),
                Arguments.of("1220", "activeStatus", "y", "Account Status must be Y or N."),
                Arguments.of("1225", "firstName", "", "First Name must be supplied."),
                Arguments.of("1225", "firstName", "O'Name", "First Name can have alphabets only."),
                Arguments.of("1235", "middleName", "1", "Middle Name can have alphabets only."),
                Arguments.of("1215", "addressLine1", "", "Address Line 1 must be supplied."),
                Arguments.of("1245", "ficoCreditScore", "", "FICO Score must be supplied."),
                Arguments.of("1245", "ficoCreditScore", "abc", "FICO Score must be all numeric."),
                Arguments.of("1245", "ficoCreditScore", "000", "FICO Score must not be zero."),
                Arguments.of("1250", "creditLimit", "", "Credit Limit must be supplied."),
                Arguments.of("1250", "creditLimit", "abc", "Credit Limit is not valid"),
                Arguments.of("1250", "creditLimit", "1E3", "Credit Limit is not valid"),
                Arguments.of("1265", "socialSecurityNumber", "000123456", "SSN: First 3 chars must not be zero."),
                Arguments.of("1265", "socialSecurityNumber", "666123456", "SSN: First 3 chars: should not be 000, 666, or between 900 and 999"),
                Arguments.of("1265", "socialSecurityNumber", "900123456", "SSN: First 3 chars: should not be 000, 666, or between 900 and 999"),
                Arguments.of("1265", "socialSecurityNumber", "123003456", "SSN 4th & 5th chars must not be zero."),
                Arguments.of("1265", "socialSecurityNumber", "123450000", "SSN Last 4 chars must not be zero."),
                Arguments.of("1270", "stateCode", "XX", "State: is not a valid state code"),
                Arguments.of("1270", "stateCode", "ca", "State: is not a valid state code"),
                Arguments.of("1275", "ficoCreditScore", "299", "FICO Score: should be between 300 and 850"),
                Arguments.of("1275", "ficoCreditScore", "851", "FICO Score: should be between 300 and 850"),
                Arguments.of("1280", "zipCode", "10001", "Invalid zip code for state"),
                Arguments.of("1245", "electronicFundsTransferAccountId", "0000000000", "EFT Account Id must not be zero."),
                Arguments.of("1220", "primaryCardholderIndicator", "X", "Primary Card Holder must be Y or N."),
                Arguments.of("EDIT-YEAR", "openDate", "", "Open Date : Year must be supplied."),
                Arguments.of("EDIT-YEAR", "openDate", "abcd0101", "Open Date must be 4 digit number."),
                Arguments.of("EDIT-YEAR", "openDate", "2100-01-01", "Open Date : Century is not valid."),
                Arguments.of("EDIT-MONTH", "openDate", "2020", "Open Date : Month must be supplied."),
                Arguments.of("EDIT-MONTH", "openDate", "2020-13-01", "Open Date: Month must be a number between 1 and 12."),
                Arguments.of("EDIT-DAY", "openDate", "2020-01", "Open Date : Day must be supplied."),
                Arguments.of("EDIT-DAY", "openDate", "2020-01-00", "Open Date:day must be a number between 1 and 31."),
                Arguments.of("EDIT-DAY-MONTH-YEAR", "openDate", "2020-04-31", "Open Date:Cannot have 31 days in this month."),
                Arguments.of("EDIT-DAY-MONTH-YEAR", "openDate", "2020-02-30", "Open Date:Cannot have 30 days in this month."),
                Arguments.of("EDIT-DAY-MONTH-YEAR", "openDate", "1900-02-29", "Open Date:Not a leap year.Cannot have 29 days in this month."),
                Arguments.of("EDIT-DATE-OF-BIRTH", "dateOfBirth", LocalDate.now().toString(), "Date of Birth:cannot be in the future"),
                Arguments.of("EDIT-AREA-CODE", "phoneNumber1", "(   )234-5678", "Phone Number 1: Area code must be supplied."),
                Arguments.of("EDIT-AREA-CODE", "phoneNumber1", "(abc)234-5678", "Phone Number 1: Area code must be A 3 digit number."),
                Arguments.of("EDIT-AREA-CODE", "phoneNumber1", "(000)234-5678", "Phone Number 1: Area code cannot be zero"),
                Arguments.of("EDIT-AREA-CODE", "phoneNumber1", "(800)234-5678", "Phone Number 1: Not valid North America general purpose area code"),
                Arguments.of("EDIT-US-PHONE-PREFIX", "phoneNumber1", "(201)   -5678", "Phone Number 1: Prefix code must be supplied."),
                Arguments.of("EDIT-US-PHONE-PREFIX", "phoneNumber1", "(201)abc-5678", "Phone Number 1: Prefix code must be A 3 digit number."),
                Arguments.of("EDIT-US-PHONE-PREFIX", "phoneNumber1", "(201)000-5678", "Phone Number 1: Prefix code cannot be zero"),
                Arguments.of("EDIT-US-PHONE-LINENUM", "phoneNumber1", "(201)234-    ", "Phone Number 1: Line number code must be supplied."),
                Arguments.of("EDIT-US-PHONE-LINENUM", "phoneNumber1", "(201)234-abcd", "Phone Number 1: Line number code must be A 4 digit number."),
                Arguments.of("EDIT-US-PHONE-LINENUM", "phoneNumber1", "(201)234-0000", "Phone Number 1: Line number code cannot be zero"));
    }

    @ParameterizedTest(name = "{0}: {1}={2}")
    @MethodSource("paragraphErrors")
    void paragraphRejectsInvalidField(String paragraph, String key, String value, String message) {
        var fields = valid(); fields.put(key, value);
        assertThat(new AccountUpdateEdits(fields).validate()).isEqualTo(new AccountUpdateEdits.Invalid(key,
                message.substring(0, Math.min(75, message.length())).stripTrailing()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2000-02-29", "2024-02-29", "1900-01-01", "2099-12-31"})
    void editDateAcceptsCenturyAndLeapBoundaries(String value) {
        var fields = valid(); fields.put("openDate", value);
        assertThat(new AccountUpdateEdits(fields).validate()).isNull();
    }

    @Test
    void p1250PreservesSignedCurrencyTruncationAndPictureOverflow() {
        assertThat(AccountUpdateEdits.amount("$1,234.567")).isEqualByComparingTo("1234.56");
        assertThat(AccountUpdateEdits.amount("1234.56CR")).isEqualByComparingTo("-1234.56");
        assertThat(AccountUpdateEdits.amount("10000000001.23")).isEqualByComparingTo("1.23");
        var fields = valid(); fields.put("creditLimit", "-1.00"); fields.put("cashCreditLimit", "999999.00");
        assertThat(new AccountUpdateEdits(fields).validate()).isNull();
    }

    @Test
    void p1260BlankAreaAndPrefixIgnoreLineAndPunctuationIsNotEdited() {
        var fields = valid(); fields.put("phoneNumber1", "x   x   x1234");
        assertThat(new AccountUpdateEdits(fields).validate()).isNull();
        fields.put("phoneNumber1", "x201x234x5678");
        assertThat(new AccountUpdateEdits(fields).validate()).isNull();
    }

    @Test
    void p1205IgnoresCaseWhitespaceAndMoneyFormattingAndSkipsLegacyInvalidUnchangedData() {
        var before = valid(); before.put("stateCode", "TS");
        var after = new LinkedHashMap<>(before); after.put("firstName", " example "); after.put("creditLimit", "+9,000.00");
        assertThat(AccountUpdateService.same(before, after)).isTrue();
        after.put("lastName", "Changed");
        assertThat(AccountUpdateService.same(before, after)).isFalse();
    }

    @Test
    void p1100NormalizesStarMissingAndBmsFieldWidths() {
        assertThat(AccountUpdateService.normalize(Map.of("firstName", "*", "zipCode", "90210-1234")))
                .containsEntry("firstName", "").containsEntry("zipCode", "90210").containsEntry("lastName", "");
        assertThat(AccountUpdateService.normalize(null).values()).allMatch(String::isEmpty);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "*", "*  "})
    void p1210BlankKey(String key) { assertThat(AccountUpdateService.keyError(key)).isEqualTo("No input received"); }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000", "42", "0000000004X", "０００００００００４２"})
    void p1210InvalidKey(String key) {
        assertThat(AccountUpdateService.keyError(key)).isEqualTo("Account Number if supplied must be a 11 digit Non-Zero Number");
    }
}
