package com.moriah.skillhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SkillHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillHubApplication.class, args);
    }
}
