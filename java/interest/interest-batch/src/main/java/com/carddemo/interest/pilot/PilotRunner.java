package com.carddemo.interest.pilot;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/** Command-line entry: {@code --interest.pilot.run-on-startup=true --interest.pilot.input-dir=... --interest.pilot.output-dir=...}. */
@Component
public class PilotRunner implements ApplicationRunner, ExitCodeGenerator {

    private final PilotRunService service;
    private final PilotProperties properties;
    private int exitCode;

    public PilotRunner(PilotRunService service, PilotProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (properties.runOnStartup()) {
            exitCode = service.run(properties.inputDir(), properties.outputDir()).exitCode();
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
