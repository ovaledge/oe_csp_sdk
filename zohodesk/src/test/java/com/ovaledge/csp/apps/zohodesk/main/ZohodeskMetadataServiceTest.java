package com.ovaledge.csp.apps.zohodesk.main;

import com.ovaledge.csp.apps.zohodesk.constants.ZohodeskConstants;
import com.ovaledge.csp.v3.core.apps.model.SupportedObject;
import com.ovaledge.csp.v3.core.apps.model.response.SupportedObjectsResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZohodeskMetadataServiceTest {

    @Test
    void getSupportedObjects_returnsEntitiesAndReports() {
        ZohodeskMetadataService service = new ZohodeskMetadataService();
        SupportedObjectsResponse response = service.getSupportedObjects();
        assertTrue(response.isSuccess());
        List<SupportedObject> supported = response.getSupportedObjects();
        assertNotNull(supported);
        assertEquals(2, supported.size());
        assertTrue(supported.stream().anyMatch(o ->
                "Entities".equals(o.getDisplayName())
                        && com.ovaledge.csp.v3.core.apps.model.ObjectKind.ENTITY.value().equals(o.getTypeName())));
        assertTrue(supported.stream().anyMatch(o ->
                ZohodeskConstants.DISPLAY_NAME_REPORTS.equals(o.getDisplayName())
                        && com.ovaledge.csp.v3.core.apps.model.ObjectKind.REPORT.value().equals(o.getTypeName())));
    }

    @Test
    void resolveSubtype_prefersObjectSubtypeFilter() {
        String subtype = ZohodeskMetadataService.resolveSubtype(
                List.of(Map.of(ZohodeskConstants.FILTER_OBJECT_SUBTYPE, ZohodeskConstants.OBJECT_SUBTYPE_AGENTS)),
                ZohodeskConstants.OBJECT_SUBTYPE_TICKETS);
        assertEquals(ZohodeskConstants.OBJECT_SUBTYPE_AGENTS, subtype);
    }

    @Test
    void inferType_mapsPrimitiveAndCompositeValues() {
        assertEquals("INTEGER", ZohodeskMetadataService.inferType(100));
        assertEquals("FLOAT", ZohodeskMetadataService.inferType(10.5d));
        assertEquals("BOOLEAN", ZohodeskMetadataService.inferType(true));
        assertEquals("ARRAY", ZohodeskMetadataService.inferType(List.of("a")));
        assertEquals("OBJECT", ZohodeskMetadataService.inferType(Map.of("k", "v")));
        assertEquals("STRING", ZohodeskMetadataService.inferType("value"));
    }

    @Test
    void subtypeFromEntityId_defaultsToTickets() {
        assertEquals(ZohodeskConstants.OBJECT_SUBTYPE_TICKETS, ZohodeskMetadataService.subtypeFromEntityId(null));
        assertEquals(ZohodeskConstants.OBJECT_SUBTYPE_CONTACTS, ZohodeskMetadataService.subtypeFromEntityId("contacts"));
        assertEquals(ZohodeskConstants.OBJECT_SUBTYPE_ACCOUNTS, ZohodeskMetadataService.subtypeFromEntityId("account-1"));
        assertEquals(ZohodeskConstants.OBJECT_SUBTYPE_DEPARTMENTS, ZohodeskMetadataService.subtypeFromEntityId("department-1"));
        assertEquals(ZohodeskConstants.OBJECT_SUBTYPE_AGENTS, ZohodeskMetadataService.subtypeFromEntityId("agent-1"));
        assertEquals(ZohodeskConstants.OBJECT_SUBTYPE_REPORTS, ZohodeskMetadataService.subtypeFromEntityId("report-1"));
    }

    @Test
    void endpointForSubtype_returnsExpectedPath() {
        assertEquals(ZohodeskConstants.ENDPOINT_TICKETS, ZohodeskMetadataService.endpointForSubtype(ZohodeskConstants.OBJECT_SUBTYPE_TICKETS));
        assertEquals(ZohodeskConstants.ENDPOINT_CONTACTS, ZohodeskMetadataService.endpointForSubtype(ZohodeskConstants.OBJECT_SUBTYPE_CONTACTS));
        assertEquals(ZohodeskConstants.ENDPOINT_ACCOUNTS, ZohodeskMetadataService.endpointForSubtype(ZohodeskConstants.OBJECT_SUBTYPE_ACCOUNTS));
        assertEquals(ZohodeskConstants.ENDPOINT_DEPARTMENTS, ZohodeskMetadataService.endpointForSubtype(ZohodeskConstants.OBJECT_SUBTYPE_DEPARTMENTS));
        assertEquals(ZohodeskConstants.ENDPOINT_AGENTS, ZohodeskMetadataService.endpointForSubtype(ZohodeskConstants.OBJECT_SUBTYPE_AGENTS));
        assertEquals(ZohodeskConstants.ENDPOINT_REPORTS, ZohodeskMetadataService.endpointForSubtype(ZohodeskConstants.OBJECT_SUBTYPE_REPORTS));
    }
}
