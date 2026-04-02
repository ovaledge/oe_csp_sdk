package com.ovaledge.csp.apps.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;

/**
 * Unified CSP Application for all Apps connectors.
 * <p>
 * This application discovers all available connectors via ServiceLoader
 * and routes requests to the appropriate connector based on serverType.
 * <p>
 * Individual connector applications (MonetDBQuickApplication)
 * can still be run standalone for testing individual connectors.
 * <p>
 * {@link HttpClientAutoConfiguration} is excluded: Hadoop brings Jetty 9.x while that
 * auto-configuration expects Jetty 12 client types. This app does not use Spring
 * {@code RestClient} beans; exclude avoids a classpath clash without adding Jetty 12.
 */
@SpringBootApplication(scanBasePackages = "com.ovaledge.csp.apps", exclude = {DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class, MongoAutoConfiguration.class, MongoDataAutoConfiguration.class,
        HttpClientAutoConfiguration.class})
public class CspAppsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CspAppsApiApplication.class, args);
    }
}
