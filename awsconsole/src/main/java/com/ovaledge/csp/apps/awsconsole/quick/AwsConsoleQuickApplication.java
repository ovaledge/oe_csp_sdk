package com.ovaledge.csp.apps.awsconsole.quick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Standalone Spring Boot application for local testing of the AWS Console connector.
 * Run this class to start a server with endpoints under /api/awsconsole (health, supported-objects, connection/validate).
 */
@SpringBootApplication(
        scanBasePackages = "com.ovaledge.csp.apps.awsconsole",
        exclude = { DataSourceAutoConfiguration.class }
)
public class AwsConsoleQuickApplication {

    public static void main(String[] args) {
        SpringApplication.run(AwsConsoleQuickApplication.class, args);
    }
}
