package com.ovaledge.csp.tests.unit.connector.cspapi;

import com.ovaledge.csp.apps.app.generator.PrimaryObjectResolver;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrimaryObjectResolverTest {

    @Test
    void resolve_entityOnly_returnsTvc() {
        assertEquals(PrimaryObjectResolver.TVC,
                PrimaryObjectResolver.resolve(List.of(ObjectKind.ENTITY), null));
    }

    @Test
    void resolve_viewOnly_returnsTvc() {
        assertEquals(PrimaryObjectResolver.TVC,
                PrimaryObjectResolver.resolve(List.of(ObjectKind.VIEW), null));
    }

    @Test
    void resolve_reportOnly_returnsR() {
        assertEquals(PrimaryObjectResolver.REPORTS,
                PrimaryObjectResolver.resolve(List.of(ObjectKind.REPORT), null));
    }

    @Test
    void resolve_fileFoldersOnly_returnsFf() {
        assertEquals(PrimaryObjectResolver.FILE_FOLDERS,
                PrimaryObjectResolver.resolve(List.of(ObjectKind.FILEFOLDERS), null));
    }

    @Test
    void resolve_datasetOnly_returnsDs() {
        assertEquals(PrimaryObjectResolver.DATASETS,
                PrimaryObjectResolver.resolve(List.of(ObjectKind.DATASET), null));
    }

    @Test
    void resolve_datasetAndFile_prefersDs() {
        assertEquals(PrimaryObjectResolver.DATASETS,
                PrimaryObjectResolver.resolve(List.of(ObjectKind.DATASET, ObjectKind.FILEFOLDERS), null));
    }

    @Test
    void resolve_entityAndReport_prefersTvc() {
        assertEquals(PrimaryObjectResolver.TVC,
                PrimaryObjectResolver.resolve(List.of(ObjectKind.ENTITY, ObjectKind.REPORT), null));
    }

    @Test
    void resolve_explicitOverride_wins() {
        assertEquals(PrimaryObjectResolver.REPORTS,
                PrimaryObjectResolver.resolve(List.of(ObjectKind.ENTITY), "r"));
    }

    @Test
    void resolve_invalidOverride_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PrimaryObjectResolver.resolve(List.of(ObjectKind.ENTITY), "TABLE"));
        assertEquals(
                "Invalid primaryObject override 'TABLE'. Valid values: [TVC, R, DS, FF]",
                ex.getMessage());
    }

    @Test
    void resolve_functionOnly_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PrimaryObjectResolver.resolve(List.of(ObjectKind.FUNCTION), null));
        assertEquals(
                "Cannot resolve primaryObject for FUNCTION/PROCEDURE-only connectors. "
                        + "Add at least one of ENTITY, VIEW, REPORT, DATASET, FILE, or FILEFOLDERS.",
                ex.getMessage());
    }

    @Test
    void resolve_emptyKinds_defaultsToTvc() {
        assertEquals(PrimaryObjectResolver.TVC, PrimaryObjectResolver.resolve(List.of(), null));
    }
}
