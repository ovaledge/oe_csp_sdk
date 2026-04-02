package com.ovaledge.csp.apps.app.generator;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Template values for connector code generation.
 */
public class TemplateValues {

    private static final String SDK_VERSION;

    public static final String RELEASE_CSP_SDK_PROPERTIES = "release-csp-sdk.properties";

    // Read SDK version only from assembly/src/main/resources/release-csp-sdk.properties
    static {

        Properties props = new Properties();

        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(RELEASE_CSP_SDK_PROPERTIES)) {

            if (in == null) {
                throw new IllegalStateException("Missing " + RELEASE_CSP_SDK_PROPERTIES + " on the classpath");
            }

            props.load(in);

            String val = props.getProperty("csp-sdk.release.version");
            if (val == null || val.isBlank()) {
                throw new IllegalStateException("Property 'csp-sdk.release.version' not set in " + RELEASE_CSP_SDK_PROPERTIES);
            }

            SDK_VERSION = val.trim();

        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + RELEASE_CSP_SDK_PROPERTIES + " from classpath", e);
        }
    }


    private TemplateValues() {
    }

    /**
     * Returns placeholder values for the archetype-based templates.
     */
    public static Map<String, String> from(ConnectorGenerationContext context) {
        Map<String, String> values = new HashMap<>();
        values.put("connectorName", context.getConnectorName());
        values.put("artifactId", context.getArtifactId());
        values.put("classPrefix", context.getClassPrefix());
        values.put("serverType", context.getServerType());
        values.put("sdkVersion", SDK_VERSION);
        values.put("__artifactId__", context.getArtifactId());
        values.put("__classPrefix__", context.getClassPrefix());
        return values;
    }
}
