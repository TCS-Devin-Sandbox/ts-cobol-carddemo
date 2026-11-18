package com.carddemo.trantype;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TrantypeApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrantypeApplication.class, args);
    }
}
