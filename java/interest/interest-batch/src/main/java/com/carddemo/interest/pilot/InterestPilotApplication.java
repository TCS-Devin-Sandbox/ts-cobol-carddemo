package com.carddemo.interest.pilot;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.PropertySource;

/** INT-01 pilot entry point: INTCALC STEP15 (CBACT04C) on the lab fixture profile. */
@SpringBootApplication
@PropertySource("classpath:pilot/pilot.properties")
@EnableConfigurationProperties(PilotProperties.class)
public class InterestPilotApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(InterestPilotApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        System.exit(SpringApplication.exit(app.run(args)));
    }
}
