package com.ovaledge.csp.tests.unit.connector.cspapi;

import com.ovaledge.csp.apps.app.generator.ConnectorGenerationContext;
import com.ovaledge.csp.apps.app.generator.TemplateValues;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateValuesTest {

    @Test
    void buildSupportedObjectsInit_reportOnly() {
        String init = supportedObjectsInit(List.of(ObjectKind.REPORT), "Onlyreports");

        assertTrue(init.contains("GENERATOR:START supported-objects"));
        assertTrue(init.contains("GENERATOR:END supported-objects"));
        assertTrue(init.contains("ObjectKind.REPORT.value()"));
        assertFalse(init.contains("ObjectKind.ENTITY.value()"));
    }

    @Test
    void buildSupportedObjectsInit_multipleKinds_preservesSelection() {
        String init = supportedObjectsInit(
                List.of(ObjectKind.DATASET, ObjectKind.REPORT, ObjectKind.FILEFOLDERS),
                "Mixed");

        assertTrue(init.contains("ObjectKind.DATASET.value()"));
        assertTrue(init.contains("ObjectKind.REPORT.value()"));
        assertTrue(init.contains("ObjectKind.FILEFOLDERS.value()"));
        assertTrue(init.indexOf("DATASET") < init.indexOf("REPORT"));
        assertTrue(init.indexOf("REPORT") < init.indexOf("FILEFOLDERS"));
    }

    @Test
    void buildSupportedObjectsInit_emptyDefaultsToEntity() {
        String init = supportedObjectsInit(List.of(), "Demo");

        assertTrue(init.contains("ObjectKind.ENTITY.value()"));
    }

    private static String supportedObjectsInit(List<ObjectKind> objectKinds, String classPrefix) {
        ConnectorGenerationContext context = new ConnectorGenerationContext(
                "test", "test", "test", classPrefix, "test", objectKinds, "TVC");
        return TemplateValues.from(context).get("supportedObjectsInit");
    }
}
