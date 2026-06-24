package com.ovaledge.csp.apps.app.generator;

import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
        values.put("packageName", context.getPackageName());
        values.put("classPrefix", context.getClassPrefix());
        values.put("serverType", context.getServerType());
        values.put("primaryObject", context.getPrimaryObject());
        values.put("supportedObjectsInit", buildSupportedObjectsInit(context.getObjectKinds(), context.getClassPrefix()));
        values.put("sdkVersion", SDK_VERSION);
        values.put("__artifactId__", context.getArtifactId());
        values.put("__packageName__", context.getPackageName());
        values.put("__classPrefix__", context.getClassPrefix());
        return values;
    }

    /**
     * Java source lines for {@code getSupportedObjects()}, one {@link SupportedObject} per selected kind.
     */
    static String buildSupportedObjectsInit(List<ObjectKind> objectKinds, String classPrefix) {
        LinkedHashSet<ObjectKind> unique = new LinkedHashSet<>();
        if (objectKinds != null) {
            for (ObjectKind kind : objectKinds) {
                if (kind != null && kind != ObjectKind.CONTAINER && kind != ObjectKind.DASHBOARD) {
                    unique.add(kind);
                }
            }
        }
        if (unique.isEmpty()) {
            unique.add(ObjectKind.ENTITY);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("        // GENERATOR:START supported-objects — scaffolded from Connector Objects selection.\n");
        sb.append("        // Customize display names, split subtypes (e.g. tables vs views), then implement crawl methods below.\n");
        for (ObjectKind kind : unique) {
            String displayName = kind.getDisplayName();
            String tooltip = kind.getTooltip();
            if (tooltip == null || tooltip.isBlank()) {
                tooltip = classPrefix + " " + kind.value();
            }
            sb.append("        types.add(new SupportedObject(ObjectKind.")
                    .append(kind.name())
                    .append(".value(), \"")
                    .append(escapeJavaString(displayName))
                    .append("\", \"")
                    .append(escapeJavaString(tooltip))
                    .append("\"));\n");
        }
        sb.append("        // GENERATOR:END supported-objects");
        return sb.toString();
    }

    private static String escapeJavaString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
