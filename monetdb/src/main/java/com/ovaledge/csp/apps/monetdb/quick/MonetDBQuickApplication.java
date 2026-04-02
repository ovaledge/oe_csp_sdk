package com.ovaledge.csp.apps.monetdb.quick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Standalone Spring Boot application for local testing of the MonetDB connector.
 * Run this class to start a server with endpoints under /api/monetdb (health, supported-objects, connection/validate).
 */
@SpringBootApplication(
        scanBasePackages = "com.ovaledge.csp.apps.monetdb",
        exclude = { DataSourceAutoConfiguration.class }
)
public class MonetDBQuickApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonetDBQuickApplication.class, args);
    }
}
