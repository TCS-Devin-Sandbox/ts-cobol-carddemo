package com.carddemo.reports;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
class ReportRequestController {
    private static final Logger log = LoggerFactory.getLogger(ReportRequestController.class);
    private static final String[] COMPONENTS = {"Month", "Day", "Year"};
    private final ReportLauncher launcher;

    ReportRequestController(ReportLauncher launcher) {
        this.launcher = launcher;
    }

    record DateFields(String month, String day, String year) {
        String[] values() {
            return new String[]{month, day, year};
        }
    }

    record Request(boolean monthly, boolean yearly, boolean custom, DateFields startDate,
                   DateFields endDate, String confirmation) { }

    record Result(String message, String severity, String focus, boolean reset, String jobId) { }

    @PostMapping
    ResponseEntity<Result> submit(@RequestBody Request request) {
        return process(request, LocalDate.now());
    }

    ResponseEntity<Result> process(Request request, LocalDate today) {
        String name;
        LocalDate start, end;
        if (request.monthly()) {
            name = "Monthly";
            var month = YearMonth.from(today);
            start = month.atDay(1);
            end = month.atEndOfMonth();
        } else if (request.yearly()) {
            name = "Yearly";
            start = today.withDayOfYear(1);
            end = today.withMonth(12).withDayOfMonth(31);
        } else if (request.custom()) {
            name = "Custom";
            try {
                var startValues = dateValues(request.startDate());
                var endValues = dateValues(request.endDate());
                requireDateFields(startValues, "Start");
                requireDateFields(endValues, "End");
                var startParts = dateParts(startValues, "Start");
                var endParts = dateParts(endValues, "End");
                start = validDate(startParts, "Start");
                end = validDate(endParts, "End");
            } catch (DateEdit e) {
                return ResponseEntity.ok(error(e.getMessage(), e.focus));
            }
        } else {
            return ResponseEntity.ok(error("Select a report type to print report...", "monthly"));
        }

        String confirmation = request.confirmation();
        if (blank(confirmation)) {
            return ResponseEntity.ok(error("Please confirm to print the " + name + " report...", "confirmation"));
        }
        if ("n".equalsIgnoreCase(confirmation)) {
            return ResponseEntity.ok(new Result(null, null, "monthly", true, null));
        }
        if (!"y".equalsIgnoreCase(confirmation)) {
            return ResponseEntity.ok(error("\"" + confirmation + "\" is not a valid value to confirm...", "confirmation"));
        }
        try {
            String id = launcher.submit(start, end);
            return ResponseEntity.accepted().body(new Result(name + " report submitted for printing ...",
                    "success", "monthly", true, id));
        } catch (IOException e) {
            log.warn("Unable to submit TRANREPT: {}", e.getMessage());
            return ResponseEntity.status(503).body(error("Unable to submit transaction report...", "monthly"));
        }
    }

    private static Result error(String message, String focus) {
        return new Result(message, "error", focus, false, null);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank() || value.chars().allMatch(c -> c == 0);
    }

    private static String[] dateValues(DateFields date) {
        return date == null ? new String[3] : date.values();
    }

    private static void requireDateFields(String[] values, String label) {
        for (int i = 0; i < values.length; i++) {
            if (blank(values[i])) throw dateError(label, i, COMPONENTS[i] + " can NOT be empty");
        }
    }

    private static int[] dateParts(String[] values, String label) {
        int[] parts = new int[3];
        int[] maxima = {12, 31, 9999};
        for (int i = 0; i < values.length; i++) {
            String value = values[i].strip();
            String pattern = i == 2 ? "[0-9]{1,4}" : "[0-9]{1,2}";
            if (!value.matches(pattern) || (parts[i] = Integer.parseInt(value)) > maxima[i]) {
                throw dateError(label, i, "Not a valid " + COMPONENTS[i]);
            }
        }
        return parts;
    }

    private static LocalDate validDate(int[] parts, String label) {
        try {
            if (parts[2] == 0) throw new DateTimeException("Year zero");
            return LocalDate.of(parts[2], parts[0], parts[1]);
        } catch (DateTimeException e) {
            throw dateError(label, 0, "Not a valid date");
        }
    }

    private static DateEdit dateError(String label, int field, String message) {
        return new DateEdit(label + " Date - " + message + "...",
                ("Start".equals(label) ? "start" : "end") + COMPONENTS[field]);
    }

    private static final class DateEdit extends RuntimeException {
        private final String focus;

        DateEdit(String message, String focus) {
            super(message);
            this.focus = focus;
        }
    }
}
