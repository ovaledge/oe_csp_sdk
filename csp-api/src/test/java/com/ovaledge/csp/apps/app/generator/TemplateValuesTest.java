package com.ovaledge.csp.apps.app.generator;

import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateValuesTest {

    @Test
    void buildSupportedObjectsInit_reportOnly() {
        String init = TemplateValues.buildSupportedObjectsInit(List.of(ObjectKind.REPORT), "Onlyreports");

        assertTrue(init.contains("GENERATOR:START supported-objects"));
        assertTrue(init.contains("GENERATOR:END supported-objects"));
        assertTrue(init.contains("ObjectKind.REPORT.value()"));
        assertFalse(init.contains("ObjectKind.ENTITY.value()"));
    }

    @Test
    void buildSupportedObjectsInit_multipleKinds_preservesSelection() {
        String init = TemplateValues.buildSupportedObjectsInit(
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
        String init = TemplateValues.buildSupportedObjectsInit(List.of(), "Demo");

        assertTrue(init.contains("ObjectKind.ENTITY.value()"));
    }
}
