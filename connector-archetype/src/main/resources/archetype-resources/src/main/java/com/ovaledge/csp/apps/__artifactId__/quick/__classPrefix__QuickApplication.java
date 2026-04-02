package com.ovaledge.csp.apps.${artifactId}.quick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = "com.ovaledge.csp.apps.${artifactId}",
        exclude = {DataSourceAutoConfiguration.class}
)
public class ${classPrefix}QuickApplication {

    public static void main(String[] args) {
        SpringApplication.run(${classPrefix}QuickApplication.class, args);
    }
}
