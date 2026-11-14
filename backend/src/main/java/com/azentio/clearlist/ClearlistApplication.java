package com.azentio.clearlist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ClearlistApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClearlistApplication.class, args);
    }
}
