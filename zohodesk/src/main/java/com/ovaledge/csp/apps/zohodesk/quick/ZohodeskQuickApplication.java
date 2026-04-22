package com.ovaledge.csp.apps.zohodesk.quick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = "com.ovaledge.csp.apps.zohodesk",
        exclude = {DataSourceAutoConfiguration.class}
)
public class ZohodeskQuickApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZohodeskQuickApplication.class, args);
    }
}
