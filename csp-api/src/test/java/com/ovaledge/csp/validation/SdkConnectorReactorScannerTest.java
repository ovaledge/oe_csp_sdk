package com.ovaledge.csp.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isolated tests for {@link SdkConnectorReactorScanner} using synthetic module trees (no full reactor checkout).
 *
 * <p>Stubs {@link StubMonetdbConnector} and {@link StubMismatchConnector} satisfy the scanner's no-arg
 * constructor and {@code getServerType()} requirements.
 */
class SdkConnectorReactorScannerTest {

    @Test
    void scan_shouldCollectOwnedTypesFromConfigAndRuntime(@TempDir Path tempDir) throws Exception {
        writeModuleOnly(tempDir, "monetdb", StubMonetdbConnector.class.getName(), "monetdb");
        writeRootPom(tempDir, "monetdb");
        SdkConnectorReactorScanner.ScanResult scan = SdkConnectorReactorScanner.scan(tempDir);
        assertTrue(scan.ownedServerTypes().contains("monetdb"));
        assertTrue(scan.configRuntimeMismatches().isEmpty());
        assertTrue(scan.runtimeInspectionErrors().isEmpty());
    }

    @Test
    void scan_shouldDetectConfigRuntimeMismatch(@TempDir Path tempDir) throws Exception {
        Path module = tempDir.resolve("badmodule");
        Path spi = module.resolve(
                "src/main/resources/META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector");
        Files.createDirectories(spi.getParent());
        Files.writeString(spi, StubMismatchConnector.class.getName());
        Files.createDirectories(module.resolve("src/main/resources/configs"));
        Files.writeString(module.resolve("src/main/resources/configs/wrongname.json"), "{}");
        writeRootPom(tempDir, "badmodule");

        SdkConnectorReactorScanner.ScanResult scan = SdkConnectorReactorScanner.scan(tempDir);
        assertEquals(1, scan.configRuntimeMismatches().size());
        assertTrue(scan.configRuntimeMismatches().get(0).contains("wrongname"));
        assertTrue(scan.configRuntimeMismatches().get(0).contains("monetdb"));
    }

    @Test
    void scan_shouldDetectDuplicateServerTypesAcrossModules(@TempDir Path tempDir) throws Exception {
        writeModuleOnly(tempDir, "moda", StubMonetdbConnector.class.getName(), "monetdb");
        writeModuleOnly(tempDir, "modb", StubMonetdbConnector.class.getName(), "monetdb");
        writeRootPom(tempDir, "moda", "modb");

        SdkConnectorReactorScanner.ScanResult scan = SdkConnectorReactorScanner.scan(tempDir);
        assertFalse(scan.duplicateServerTypesFromConfig().isEmpty());
        assertFalse(scan.duplicateServerTypesFromRuntime().isEmpty());
    }

    /** Writes one connector module with SPI registration and a single {@code configs/<basename>.json}. */
    private static void writeModuleOnly(Path repoRoot, String moduleName, String providerFqcn, String configBasename)
            throws Exception {
        Path module = repoRoot.resolve(moduleName);
        Path spi = module.resolve(
                "src/main/resources/META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector");
        Files.createDirectories(spi.getParent());
        Files.writeString(spi, providerFqcn);
        Files.createDirectories(module.resolve("src/main/resources/configs"));
        Files.writeString(module.resolve("src/main/resources/configs/" + configBasename + ".json"), "{}");
    }

    /** Writes a minimal aggregator {@code pom.xml} listing the given reactor modules. */
    private static void writeRootPom(Path repoRoot, String... modules) throws Exception {
        StringBuilder moduleXml = new StringBuilder();
        for (String module : modules) {
            moduleXml.append("    <module>").append(module).append("</module>\n");
        }
        Files.writeString(repoRoot.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId>
                  <artifactId>test-sdk</artifactId>
                  <version>1.0</version>
                  <packaging>pom</packaging>
                  <modules>
                %s  </modules>
                </project>
                """.formatted(moduleXml));
    }
}
