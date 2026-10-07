package com.flashquiz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FlashquizApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlashquizApplication.class, args);
    }
}
