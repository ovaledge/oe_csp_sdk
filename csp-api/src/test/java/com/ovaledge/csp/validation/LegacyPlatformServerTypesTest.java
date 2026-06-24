package com.ovaledge.csp.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for {@link LegacyPlatformServerTypes} reservation and dynamic in-repo blocking. */
class LegacyPlatformServerTypesTest {

    @Test
    void reservedTypes_shouldMatchArtifactStyleNormalization() {
        assertTrue(LegacyPlatformServerTypes.isForbidden("qliksense"));
        assertTrue(LegacyPlatformServerTypes.isForbidden("Qlik Sense"));
        assertTrue(LegacyPlatformServerTypes.isForbidden("quickbooksonline"));
        assertTrue(LegacyPlatformServerTypes.isForbidden("quickbooksdesktop"));
        assertTrue(LegacyPlatformServerTypes.forbiddenTypes().contains("qlik sense"));
        assertTrue(LegacyPlatformServerTypes.forbiddenTypes().contains("quickbooks-online"));
        assertTrue(LegacyPlatformServerTypes.forbiddenTypes().contains("sapbo_universe"));
    }

    @Test
    void violatesSdkGate_shouldAllowTypesOwnedInRepo(@TempDir Path tempDir) throws Exception {
        writeMinimalConnectorRepo(tempDir, "monetdb");
        var scan = SdkConnectorReactorScanner.scan(tempDir);
        assertTrue(scan.ownedServerTypes().contains("monetdb"));
        assertFalse(LegacyPlatformServerTypes.violatesSdkGate("monetdb", scan.ownedServerTypes()));
        assertTrue(LegacyPlatformServerTypes.violatesSdkGate("qliksense", scan.ownedServerTypes()));
    }

    @Test
    void blockedNamesForNewConnector_shouldIncludeTxtAndOwned(@TempDir Path tempDir) throws Exception {
        writeMinimalConnectorRepo(tempDir, "monetdb");
        var blocked = LegacyPlatformServerTypes.blockedNamesForNewConnector(tempDir);
        assertTrue(blocked.contains("monetdb"));
        assertTrue(blocked.contains("snowflake"));
        assertTrue(LegacyPlatformServerTypes.isBlockedForNewConnector("monetdb", tempDir));
        assertTrue(LegacyPlatformServerTypes.isBlockedForNewConnector("snowflake", tempDir));
        assertFalse(LegacyPlatformServerTypes.isBlockedForNewConnector("mycustomconnector", tempDir));
    }

    @Test
    void isBlockedForNewConnector_shouldBlockSpacingVariantsOfExistingModule(@TempDir Path tempDir)
            throws Exception {
        writeMinimalConnectorRepo(tempDir, "monetdb");
        assertTrue(LegacyPlatformServerTypes.isBlockedForNewConnector("Monet DB", tempDir));
        assertTrue(LegacyPlatformServerTypes.isBlockedForNewConnector("MonetDB", tempDir));
        assertTrue(LegacyPlatformServerTypes.isExactBlockedForNewConnector("Monet DB", tempDir));
    }

    /** Minimal reactor with one connector module for {@link SdkConnectorReactorScanner} integration checks. */
    private static void writeMinimalConnectorRepo(Path repoRoot, String moduleName) throws Exception {
        Files.writeString(repoRoot.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId>
                  <artifactId>test-sdk</artifactId>
                  <version>1.0</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>%s</module>
                  </modules>
                </project>
                """.formatted(moduleName));

        Path module = repoRoot.resolve(moduleName);
        Path spi = module.resolve(
                "src/main/resources/META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector");
        Files.createDirectories(spi.getParent());
        Files.writeString(spi, StubMonetdbConnector.class.getName());
        Files.createDirectories(module.resolve("src/main/resources/configs"));
        Files.writeString(module.resolve("src/main/resources/configs/" + moduleName + ".json"), "{}");
    }
}
