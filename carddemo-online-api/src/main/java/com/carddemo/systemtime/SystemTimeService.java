package com.carddemo.systemtime;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** CODATE01 4000-PROCESS-REQUEST-REPLY: ASKTIME, FORMATTIME, STRING into a blank 1000-byte reply. */
@Service
public class SystemTimeService {
    public static final int REPLY_LENGTH = 1000;
    private static final DateTimeFormatter MMDDYYYY = DateTimeFormatter.ofPattern("MM-dd-uuuu", Locale.ROOT);
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private final Clock clock;

    @Autowired
    SystemTimeService() {
        this(Clock.systemDefaultZone());
    }

    SystemTimeService(Clock clock) {
        this.clock = clock;
    }

    public record SystemTime(String date, String time, String reply) { }

    public SystemTime now() {
        LocalDateTime now = LocalDateTime.now(clock);
        String date = MMDDYYYY.format(now);
        String time = HHMMSS.format(now);
        String text = "SYSTEM DATE : " + date + "SYSTEM TIME : " + time;
        return new SystemTime(date, time, text + " ".repeat(REPLY_LENGTH - text.length()));
    }
}
