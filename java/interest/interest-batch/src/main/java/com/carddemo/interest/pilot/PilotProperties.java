package com.carddemo.interest.pilot;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Run configuration. {@code parm} (INT-H02, opaque PARM-DATE) and {@code clock} (INT-Q05 / TN-02, local
 * wall clock) are independent inputs; neither is derived from the other.
 */
@ConfigurationProperties("interest.pilot")
public record PilotProperties(
        String parm,
        LocalDateTime clock,
        ZoneId zone,
        boolean runOnStartup,
        Path inputDir,
        Path outputDir) {
}
