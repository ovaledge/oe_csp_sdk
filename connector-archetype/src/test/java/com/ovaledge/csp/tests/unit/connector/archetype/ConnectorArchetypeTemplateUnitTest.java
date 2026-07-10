package com.ovaledge.csp.tests.unit.connector.archetype;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class ConnectorArchetypeTemplateUnitTest {

    @Test
    void connectorTemplateFile_exists() {
        Path template = Paths.get("src/main/resources/archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/main/__classPrefix__Connector.java");
        assertTrue(Files.exists(template), "Expected archetype connector template at " + template);
    }

    @Test
    void unitTestTemplates_useCspSdkTestLayout() {
        Path connectorUnitTest = Paths.get(
                "src/main/resources/archetype-resources/src/test/java/com/ovaledge/csp/tests/unit/connector/__packageName__/__classPrefix__ConnectorUnitTest.java");
        Path unitPackageInfo = Paths.get(
                "src/main/resources/archetype-resources/src/test/java/com/ovaledge/csp/tests/unit/package-info.java");
        assertTrue(Files.exists(connectorUnitTest), "Expected unit test template at " + connectorUnitTest);
        assertTrue(Files.exists(unitPackageInfo), "Expected unit package-info at " + unitPackageInfo);
    }
}
