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
        // Defaults when profiling is off (empty wiring)
        values.put("profilingServiceImport", "");
        values.put("profilingServiceField", "");
        values.put("profilingServiceGetter", "");
        values.put("profilingServiceMethods", "");
        values.put("profilingServiceUnitTestBody", "");
        values.put("profilingServiceConnectorImport", "");
        values.put("profilingServiceConnectorTest", "");
        return values;
    }

    /**
     * Fills profiling-related placeholders for Connector / ProfilingService templates.
     *
     * @param values      template map from {@link #from(ConnectorGenerationContext)}
     * @param classPrefix connector class prefix
     * @param profiling   whether profiling is enabled
     * @param profilingMode {@code DBMS} or {@code SAMPLE} (ignored when profiling is false)
     */
    public static void applyProfiling(Map<String, String> values,
                                      String classPrefix,
                                      boolean profiling,
                                      String profilingMode,
                                      List<ObjectKind> objectKinds) {
        if (values == null) {
            return;
        }
        if (!profiling) {
            values.put("profilingServiceImport", "");
            values.put("profilingServiceField", "");
            values.put("profilingServiceGetter", "");
            values.put("profilingServiceMethods", "");
            values.put("profilingServiceUnitTestBody", "");
            values.put("profilingServiceConnectorImport", "");
            values.put("profilingServiceConnectorTest", "");
            return;
        }
        boolean hasEntityView = objectKinds != null
                && objectKinds.stream()
                .filter(k -> k != null)
                .anyMatch(k -> k == ObjectKind.ENTITY || k == ObjectKind.VIEW);
        boolean hasFile = objectKinds != null
                && objectKinds.stream()
                .filter(k -> k != null)
                .anyMatch(k -> k == ObjectKind.FILE);

        String mode = profilingMode != null ? profilingMode.trim().toUpperCase() : "SAMPLE";
        if (!"DBMS".equals(mode)) {
            mode = "SAMPLE";
        }
        values.put("profilingServiceImport",
                "import com.ovaledge.csp.v3.core.apps.service.ProfilingService;\n");
        values.put("profilingServiceField",
                "    private final " + classPrefix + "ProfilingService profilingService = new "
                        + classPrefix + "ProfilingService();\n");
        values.put("profilingServiceGetter",
                "\n    @Override\n"
                        + "    public ProfilingService getProfilingService() {\n"
                        + "        return profilingService;\n"
                        + "    }\n");
        StringBuilder methods = new StringBuilder();
        if (hasEntityView) {
            methods.append("DBMS".equals(mode)
                    ? buildDbmsProfilingMethods()
                    : buildSampleProfilingMethods());
        }
        if (hasFile) {
            methods.append(buildFileProfilingMethods());
        }
        values.put("profilingServiceMethods", methods.toString());
        values.put("profilingServiceUnitTestBody",
                "DBMS".equals(mode)
                        ? buildDbmsProfilingUnitTestBody(classPrefix, hasFile)
                        : buildSampleProfilingUnitTestBody(classPrefix, hasFile));
        String packageName = values.getOrDefault("packageName", "");
        values.put("profilingServiceConnectorImport",
                "import com.ovaledge.csp.apps." + packageName + ".main." + classPrefix + "ProfilingService;\n"
                        + "import com.ovaledge.csp.v3.core.apps.service.ProfilingService;\n");
        values.put("profilingServiceConnectorTest",
                "\n    @Test\n"
                        + "    void getProfilingService_returnsProfilingServiceInstance() {\n"
                        + "        ProfilingService profilingService = connector.getProfilingService();\n"
                        + "        assertNotNull(profilingService);\n"
                        + "        assertTrue(profilingService instanceof " + classPrefix + "ProfilingService);\n"
                        + "    }\n");
    }

    private static String buildDbmsProfilingMethods() {
        return """
            @Override
            public List<String> getSkipDataTypesForProfile() {
                // TODO: return dialect-specific types to soft-skip during column profiling (e.g. blob, clob, json).
                return Collections.emptyList();
            }

            @Override
            public long getRowCount(ProfileRowCountRequest request) {
                requireObjectKind(request, ObjectKind.ENTITY, ObjectKind.VIEW);
                validateIdentity(request.getConnectionConfig(), request.getContainerId(), request.getEntityId());
                // TODO: implement using JdbcProfilingSqlUtils.buildRowCountSql + ConnectionPoolManager JDBC resource.
                return 0L;
            }

            @Override
            public ProfileColumnResult profileColumn(ProfileColumnRequest request) {
                requireObjectKind(request, ObjectKind.ENTITY, ObjectKind.VIEW);
                validateIdentity(request.getConnectionConfig(), request.getContainerId(), request.getEntityId());
                // TODO: implement aggregate column profiling (JdbcProfilingSqlUtils + soft-skip / null-count paths).
                ProfileColumnResult result = new ProfileColumnResult();
                result.setMessage("TODO: implement profileColumn");
                return result;
            }

            @Override
            public Map<String, ProfileColumnResult> sampleProfile(SampleProfileRequest request) {
                requireObjectKind(request, ObjectKind.ENTITY, ObjectKind.VIEW);
                validateIdentity(request.getConnectionConfig(), request.getContainerId(), request.getEntityId());
                // TODO: fetch sample rows and compute stats via ProfileStatsCalculator (page merge as needed).
                return new LinkedHashMap<>();
            }
            """;
    }

    private static String buildSampleProfilingMethods() {
        return """
            @Override
            public List<String> getSkipDataTypesForProfile() {
                // TODO: return types to soft-skip during sample profiling if applicable.
                return Collections.emptyList();
            }

            @Override
            public long getRowCount(ProfileRowCountRequest request) {
                requireObjectKind(request, ObjectKind.ENTITY, ObjectKind.VIEW);
                validateIdentity(request.getConnectionConfig(), request.getContainerId(), request.getEntityId());
                // Required for SAMPLE: OvalEdge calls getRowCount before sampleProfile and skips the
                // object on ProfilingUnsupportedException or a zero count. Honor rowCountLimit when set.
                // TODO: count source records for this entity (do not return 0 when data exists).
                return 0L;
            }

            @Override
            public Map<String, ProfileColumnResult> sampleProfile(SampleProfileRequest request) {
                requireObjectKind(request, ObjectKind.ENTITY, ObjectKind.VIEW);
                validateIdentity(request.getConnectionConfig(), request.getContainerId(), request.getEntityId());
                if (request.getFields() == null || request.getFields().isEmpty()) {
                    // TODO: optionally require fields; empty map is a compilable placeholder.
                    return new LinkedHashMap<>();
                }
                // TODO: fetch sample data for the object and compute ProfileColumnResult per field
                // using ProfileStatsCalculator (or connector-specific sample logic for non-JDBC sources).
                return new LinkedHashMap<>();
            }
            """;
    }

    private static String buildFileProfilingMethods() {
        return """
            @Override
            public FileProfileResponse profileFile(FileProfileRequest request) {
                requireObjectKind(request, ObjectKind.FILE);
                validateIdentity(request.getConnectionConfig(), request.getContainerId(), request.getEntityId());
                // TODO: implement legacy FilesDtoInterface.profileFile parity
                // (parse + column stats mapping into FileProfileResponse).
                return new FileProfileResponse();
            }
            """;
    }

    private static String buildDbmsProfilingUnitTestBody(String classPrefix, boolean hasFileKind) {
        String dbmsBody = """
            private final %sProfilingService profilingService = new %sProfilingService();

            @Test
            void getSkipDataTypesForProfile_returnsNonNullList() {
                assertNotNull(profilingService.getSkipDataTypesForProfile());
            }

            @Test
            void getRowCount_returnsZeroForStub() {
                ProfileRowCountRequest request = new ProfileRowCountRequest(
                        new ConnectionConfig(), ObjectKind.ENTITY, "schema", "table");
                assertEquals(0L, profilingService.getRowCount(request));
            }

            @Test
            void sampleProfile_returnsEmptyMapForStub() {
                SampleProfileRequest request = new SampleProfileRequest(
                        new ConnectionConfig(), ObjectKind.ENTITY, "schema", "table");
                Map<String, ProfileColumnResult> result = profilingService.sampleProfile(request);
                assertNotNull(result);
                assertTrue(result.isEmpty());
            }
            """.formatted(classPrefix, classPrefix);
        if (!hasFileKind) {
            return dbmsBody;
        }
        return dbmsBody + buildFileProfilingUnitTestBody();
    }

    private static String buildSampleProfilingUnitTestBody(String classPrefix, boolean hasFileKind) {
        String sampleBody = """
            private final %sProfilingService profilingService = new %sProfilingService();

            @Test
            void sampleProfile_returnsEmptyMapForStub() {
                SampleProfileRequest request = new SampleProfileRequest(
                        new ConnectionConfig(), ObjectKind.ENTITY, "schema", "table");
                Map<String, ProfileColumnResult> result = profilingService.sampleProfile(request);
                assertNotNull(result);
                assertTrue(result.isEmpty());
            }

            @Test
            void getRowCount_returnsZeroForStub() {
                ProfileRowCountRequest request = new ProfileRowCountRequest(
                        new ConnectionConfig(), ObjectKind.ENTITY, "schema", "table");
                assertEquals(0L, profilingService.getRowCount(request));
            }
            """.formatted(classPrefix, classPrefix);
        if (!hasFileKind) {
            return sampleBody;
        }
        return sampleBody + buildFileProfilingUnitTestBody();
    }

    private static String buildFileProfilingUnitTestBody() {
        return """

            @Test
            void profileFile_returnsEmptyResponseForStub() {
                FileProfileRequest request = new FileProfileRequest(
                        new ConnectionConfig(), ObjectKind.FILE, "folder", "file.csv", "/data/folder/file.csv");
                FileProfileResponse result = profilingService.profileFile(request);
                assertNotNull(result);
                assertNotNull(result.getProfileResult());
            }

            @Test
            void profileFile_unsupportedKind_entity_throwsUnsupported() {
                FileProfileRequest request = new FileProfileRequest(
                        new ConnectionConfig(), ObjectKind.ENTITY, "folder", "file.csv", "/data/folder/file.csv");
                assertThrows(ProfilingUnsupportedException.class, () -> profilingService.profileFile(request));
            }
            """;
    }

    /**
     * Java source lines for {@code getSupportedObjects()}, one {@link com.ovaledge.csp.v3.core.apps.model.SupportedObject} per selected kind.
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
