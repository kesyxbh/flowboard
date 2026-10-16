package com.flowboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// UserDetailsServiceAutoConfiguration is excluded because authentication here is JWT only. Without the exclusion
// Spring Boot creates an in-memory "user" with a random password and prints it on every start.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class FlowBoardApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowBoardApplication.class, args);
    }
}
