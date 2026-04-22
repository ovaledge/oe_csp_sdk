package com.ovaledge.csp.apps.zohodesk.main;

import com.ovaledge.csp.apps.zohodesk.constants.ZohodeskConstants;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import com.ovaledge.csp.v3.core.apps.model.request.QueryRequest;
import com.ovaledge.csp.v3.core.apps.model.response.QueryResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZohodeskQueryServiceTest {

    @Test
    void fetchData_invalidRequest_returnsFailure() throws Exception {
        ZohodeskQueryService service = new ZohodeskQueryService();
        QueryResponse response = service.fetchData(new QueryRequest());
        assertFalse(response.isSuccess());
        assertEquals(0, response.getTotalRows());
    }

    @Test
    void resolveSubtype_usesEntityIdAndFilters() {
        QueryRequest contactsById = new QueryRequest().withEntityId("contacts");
        assertEquals(ZohodeskConstants.OBJECT_SUBTYPE_CONTACTS, ZohodeskQueryService.resolveSubtype(contactsById, false));

        QueryRequest agentsByFilter = new QueryRequest().withFilters(
                List.of(Map.of(ZohodeskConstants.FILTER_OBJECT_SUBTYPE, ZohodeskConstants.OBJECT_SUBTYPE_AGENTS)));
        assertEquals(ZohodeskConstants.OBJECT_SUBTYPE_AGENTS, ZohodeskQueryService.resolveSubtype(agentsByFilter, false));

        QueryRequest reportsRequest = new QueryRequest().withEntityType(ObjectKind.REPORT.value());
        assertEquals(ZohodeskConstants.OBJECT_SUBTYPE_REPORTS, ZohodeskQueryService.resolveSubtype(reportsRequest, true));
    }

    @Test
    void paginationAndRetryHelpers_behaveAsExpected() {
        assertEquals(ZohodeskConstants.OBJECT_SUBTYPE_REPORTS, ZohodeskMetadataService.mapDisplayNameToSubtype("Reports"));
        assertEquals(ZohodeskConstants.OBJECT_SUBTYPE_AGENTS, ZohodeskMetadataService.mapDisplayNameToSubtype("Agents"));
        assertEquals(ZohodeskConstants.OBJECT_SUBTYPE_TICKETS, ZohodeskMetadataService.mapDisplayNameToSubtype("Unknown"));
    }

    @Test
    void normalizeReportRows_prefersTabularDataAndFieldSelection() {
        Map<String, Object> reportRow = new LinkedHashMap<>();
        reportRow.put("name", "A");
        reportRow.put("reportType", "table");
        reportRow.put("id", "1");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", List.of(reportRow));

        List<Map<String, Object>> rows = ZohodeskQueryService.normalizeReportRows(
                response,
                List.of("name", "reportType"),
                50,
                0);

        assertEquals(1, rows.size());
        assertEquals("A", rows.get(0).get("name"));
        assertEquals("table", rows.get(0).get("reportType"));
    }

    @Test
    void normalizeReportRows_fallbacksToSingleDetailObject() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", "1290");
        response.put("name", "SLA Report");
        response.put("isEditable", true);

        List<Map<String, Object>> rows = ZohodeskQueryService.normalizeReportRows(
                response,
                new ArrayList<>(),
                10,
                0);

        assertEquals(1, rows.size());
        assertEquals("1290", rows.get(0).get("id"));
        assertEquals("SLA Report", rows.get(0).get("name"));
    }

}
