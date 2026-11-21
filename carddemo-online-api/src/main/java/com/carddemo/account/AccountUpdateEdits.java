package com.carddemo.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Pattern;

final class AccountUpdateEdits {
    private static final Pattern CURRENCY = Pattern.compile(
            "(?<prefix>[+-] *\\$?|\\$ *[+-]?)? *(?<number>[0-9][0-9,]*(?:\\.[0-9]*)?|\\.[0-9]+) *(?<suffix>[+-]|CR|DB)?");
    record Invalid(String field, String message) { }
    private final Map<String, String> fields;

    AccountUpdateEdits(Map<String, String> fields) { this.fields = fields; }

    String value(String key) { return fields.getOrDefault(key, ""); }

    Invalid validate() {
        try {
            yesNo("activeStatus", "Account Status");
            date("openDate", "Open Date", false);
            money("creditLimit", "Credit Limit");
            date("expirationDate", "Expiry Date", false);
            money("cashCreditLimit", "Cash Credit Limit");
            date("reissueDate", "Reissue Date", false);
            money("currentBalance", "Current Balance");
            money("currentCycleCredit", "Current Cycle Credit Limit");
            money("currentCycleDebit", "Current Cycle Debit Limit");
            String ssn = value("socialSecurityNumber");
            String first = part(ssn, 0, 3);
            numeric("socialSecurityNumber", "SSN: First 3 chars", first, 3);
            if (first.equals("666") || Integer.parseInt(first) >= 900) {
                fail("socialSecurityNumber", "SSN: First 3 chars: should not be 000, 666, or between 900 and 999");
            }
            numeric("socialSecurityNumber", "SSN 4th & 5th chars", part(ssn, 3, 5), 2);
            numeric("socialSecurityNumber", "SSN Last 4 chars", part(ssn, 5, 9), 4);
            date("dateOfBirth", "Date of Birth", true);
            numeric("ficoCreditScore", "FICO Score", value("ficoCreditScore"), 3);
            int fico = Integer.parseInt(value("ficoCreditScore"));
            if (fico < 300 || fico > 850) fail("ficoCreditScore", "FICO Score: should be between 300 and 850");
            alpha("firstName", "First Name", false);
            alpha("middleName", "Middle Name", true);
            alpha("lastName", "Last Name", false);
            required("addressLine1", "Address Line 1");
            alpha("stateCode", "State", false);
            if (!AccountUpdateLookups.STATES.contains(value("stateCode"))) {
                fail("stateCode", "State: is not a valid state code");
            }
            numeric("zipCode", "Zip", part(value("zipCode"), 0, 5), 5);
            alpha("addressLine3", "City", false);
            alpha("countryCode", "Country", false);
            phone("phoneNumber1", "Phone Number 1");
            phone("phoneNumber2", "Phone Number 2");
            numeric("electronicFundsTransferAccountId", "EFT Account Id", value("electronicFundsTransferAccountId"), 10);
            yesNo("primaryCardholderIndicator", "Primary Card Holder");
            if (!AccountUpdateLookups.STATE_ZIPS.contains(value("stateCode") + part(value("zipCode"), 0, 2))) {
                fail("zipCode", "Invalid zip code for state");
            }
            return null;
        } catch (EditFailure error) {
            return new Invalid(error.field, error.getMessage());
        }
    }

    private void required(String key, String label) {
        if (value(key).isBlank()) fail(key, label + " must be supplied.");
    }

    private void yesNo(String key, String label) {
        required(key, label);
        if (!value(key).matches("[YN]")) fail(key, label + " must be Y or N.");
    }

    private void alpha(String key, String label, boolean optional) {
        if (!optional) required(key, label);
        if (!value(key).matches("[A-Za-z ]*")) fail(key, label + " can have alphabets only.");
    }

    private void numeric(String key, String label, String value, int size) {
        if (value.isBlank()) fail(key, label + " must be supplied.");
        if (!value.matches("[0-9]{" + size + "}")) fail(key, label + " must be all numeric.");
        if (Long.parseLong(value) == 0) fail(key, label + " must not be zero.");
    }

    private void money(String key, String label) {
        required(key, label);
        try {
            amount(value(key));
        } catch (NumberFormatException error) {
            fail(key, label + " is not valid");
        }
    }

    static BigDecimal amount(String value) {
        var match = CURRENCY.matcher(value.trim());
        if (!match.matches()) throw new NumberFormatException();
        String prefix = match.group("prefix"), suffix = match.group("suffix");
        if (prefix != null && (prefix.contains("+") || prefix.contains("-")) && suffix != null) {
            throw new NumberFormatException();
        }
        String number = match.group("number").replace(",", "");
        if ((prefix != null && prefix.contains("-")) || "-".equals(suffix)
                || "CR".equals(suffix) || "DB".equals(suffix)) number = "-" + number;
        return new BigDecimal(number).setScale(2, java.math.RoundingMode.DOWN).remainder(new BigDecimal("10000000000"));
    }

    private void date(String key, String label, boolean birth) {
        String value = value(key).replace("-", "");
        String year = part(value, 0, 4), month = part(value, 4, 6), day = part(value, 6, 8);
        if (year.isBlank()) fail(key, label + " : Year must be supplied.");
        if (!year.matches("[0-9]{4}")) fail(key, label + " must be 4 digit number.");
        int y = Integer.parseInt(year);
        if (y < 1900 || y > 2099) fail(key, label + " : Century is not valid.");
        if (month.isBlank()) fail(key, label + " : Month must be supplied.");
        if (!month.matches("[0-9]{2}") || Integer.parseInt(month) < 1 || Integer.parseInt(month) > 12) {
            fail(key, label + ": Month must be a number between 1 and 12.");
        }
        if (day.isBlank()) fail(key, label + " : Day must be supplied.");
        if (!day.matches("[0-9]{2}") || Integer.parseInt(day) < 1 || Integer.parseInt(day) > 31) {
            fail(key, label + ":day must be a number between 1 and 31.");
        }
        int m = Integer.parseInt(month), d = Integer.parseInt(day);
        if (d == 31 && (m == 2 || m == 4 || m == 6 || m == 9 || m == 11)) fail(key, label + ":Cannot have 31 days in this month.");
        if (m == 2 && d == 30) fail(key, label + ":Cannot have 30 days in this month.");
        if (m == 2 && d == 29 && !java.time.Year.isLeap(y)) fail(key, label + ":Not a leap year.Cannot have 29 days in this month.");
        if (birth && !LocalDate.of(y, m, d).isBefore(LocalDate.now())) fail(key, label + ":cannot be in the future");
    }

    private void phone(String key, String label) {
        String phone = String.format("%-15s", value(key));
        String area = part(phone, 1, 4), prefix = part(phone, 5, 8), line = part(phone, 9, 13);
        if (area.isBlank() && prefix.isBlank()) return;
        phonePart(key, label, area, "Area code", 3);
        if (!AccountUpdateLookups.AREA_CODES.contains(area)) fail(key, label + ": Not valid North America general purpose area code");
        phonePart(key, label, prefix, "Prefix code", 3);
        phonePart(key, label, line, "Line number code", 4);
    }

    private void phonePart(String key, String label, String value, String part, int size) {
        if (value.isBlank()) fail(key, label + ": " + part + " must be supplied.");
        if (!value.matches("[0-9]{" + size + "}")) fail(key, label + ": " + part + " must be A " + size + " digit number.");
        if (Integer.parseInt(value) == 0) fail(key, label + ": " + part + " cannot be zero");
    }

    private static String part(String value, int start, int end) {
        return value.substring(Math.min(start, value.length()), Math.min(end, value.length()));
    }

    private static void fail(String key, String message) {
        throw new EditFailure(key, message.substring(0, Math.min(75, message.length())).stripTrailing());
    }

    private static final class EditFailure extends RuntimeException {
        private final String field;
        EditFailure(String field, String message) { super(message); this.field = field; }
    }
}
