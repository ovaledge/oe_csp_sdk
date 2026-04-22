package com.ovaledge.csp.apps.zohodesk.main;

import com.ovaledge.csp.apps.zohodesk.client.ZohodeskClient;
import com.ovaledge.csp.apps.zohodesk.constants.ZohodeskConstants;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import com.ovaledge.csp.v3.core.apps.model.request.QueryRequest;
import com.ovaledge.csp.v3.core.apps.model.response.QueryResponse;
import com.ovaledge.csp.v3.core.apps.service.QueryService;
import com.ovaledge.csp.v3.core.apps.utils.Utils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZohodeskQueryService implements QueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZohodeskQueryService.class);
    private final ZohodeskClient client = new ZohodeskClient();

    @Override
    public QueryResponse fetchData(QueryRequest request) {
        if (request == null || request.getConnectionConfig() == null) {
            return new QueryResponse().withData(new ArrayList<>()).withTotalRows(0).withSuccess(false)
                    .withMessage("Invalid query request.");
        }
        String entityType = request.getEntityType();
        boolean isEntityType = entityType != null && entityType.equalsIgnoreCase(ObjectKind.ENTITY.value());
        boolean isReportType = entityType != null && entityType.equalsIgnoreCase(ObjectKind.REPORT.value());
        if (!isEntityType && !isReportType) {
            return new QueryResponse().withData(new ArrayList<>()).withTotalRows(0).withSuccess(true);
        }

        int limit = ZohodeskClient.normalizeLimit(request.getLimit());
        int offset = request.getOffset() != null ? Math.max(request.getOffset(), 0) : 0;

        try {
            if (isReportType) {
                List<Map<String, Object>> reportData = fetchReportData(request, limit, offset);
                return new QueryResponse()
                        .withData(reportData)
                        .withTotalRows(reportData.size())
                        .withSuccess(true);
            }
            String subtype = resolveSubtype(request, isReportType);
            String endpoint = ZohodeskMetadataService.endpointForSubtype(subtype);
            List<Map<String, Object>> data = client.listEntities(
                    request.getConnectionConfig(),
                    endpoint,
                    limit,
                    offset,
                    true);
            Long totalRows = resolveTotalRows(request, subtype, data, offset);
            return new QueryResponse()
                    .withData(data != null ? data : new ArrayList<>())
                    .withTotalRows(totalRows != null ? totalRows.intValue() : (data != null ? data.size() : 0))
                    .withSuccess(true);
        } catch (Exception ex) {
            LOGGER.warn("Zoho Desk fetchData failed: {}", ex.getMessage());
            return new QueryResponse().withData(new ArrayList<>()).withTotalRows(0).withSuccess(false)
                    .withMessage(ex.getMessage() != null ? ex.getMessage() : "Query execution failed.");
        }
    }

    static String resolveSubtype(QueryRequest request, boolean reportType) {
        if (request == null) {
            return reportType ? ZohodeskConstants.OBJECT_SUBTYPE_REPORTS : ZohodeskConstants.OBJECT_SUBTYPE_TICKETS;
        }
        if (request.getEntityId() != null && !request.getEntityId().isBlank()) {
            return ZohodeskMetadataService.subtypeFromEntityId(request.getEntityId());
        }
        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            return ZohodeskMetadataService.resolveSubtype(
                    request.getFilters(),
                    reportType ? ZohodeskConstants.OBJECT_SUBTYPE_REPORTS : ZohodeskConstants.OBJECT_SUBTYPE_TICKETS);
        }
        return reportType ? ZohodeskConstants.OBJECT_SUBTYPE_REPORTS : ZohodeskConstants.OBJECT_SUBTYPE_TICKETS;
    }

    private List<Map<String, Object>> fetchReportData(QueryRequest request, int limit, int offset) {
        String reportId = resolveReportId(request);
        if (Utils.isBlank(reportId)) {
            return new ArrayList<>(); // No matching report found.
        }
        String reportEndpoint = ZohodeskConstants.ENDPOINT_REPORTS + "/" + reportId;
        Map<String, Object> reportResponse = client.getObject(request.getConnectionConfig(), reportEndpoint, true);
        return normalizeReportRows(reportResponse, request.getFields(), limit, offset);
    }

    private String resolveReportId(QueryRequest request) {
        if (request == null) {
            return null;
        }
        String entityId = request.getEntityId();
        if (isNumeric(entityId)) {
            return entityId;
        }
        String explicitFilterId = getFilterValue(request.getFilters(), "reportId");
        if (isNumeric(explicitFilterId)) {
            return explicitFilterId;
        }
        String searchTerm = firstNonBlank(
                entityId,
                getFilterValue(request.getFilters(), "reportName"),
                getFilterValue(request.getFilters(), "name"),
                getFilterValue(request.getFilters(), "reportType"));
        if (Utils.isBlank(searchTerm)) {
            return null;
        }
        List<Map<String, Object>> reports = client.listEntities(
                request.getConnectionConfig(),
                ZohodeskConstants.ENDPOINT_REPORTS,
                ZohodeskConstants.MAX_PAGE_SIZE,
                0,
                true);
        String normalizedSearch = normalize(searchTerm);
        for (Map<String, Object> report : reports) {
            String id = Utils.asString(report.get("id"));
            if (Utils.isBlank(id)) {
                continue;
            }
            String name = firstNonBlank(
                    Utils.asString(report.get("name")),
                    Utils.asString(report.get("reportName")),
                    Utils.asString(report.get("displayName")),
                    Utils.asString(report.get("title")));
            if (normalizedSearch.equals(normalize(name))) {
                return id;
            }
        }
        return null;
    }

    static List<Map<String, Object>> normalizeReportRows(
            Map<String, Object> response,
            List<String> requestedFields,
            int limit,
            int offset) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (response == null || response.isEmpty()) {
            return rows;
        }

        // If API returns tabular report payload under data/rows/records/results, map them directly.
        for (String key : new String[]{"data", "rows", "records", "results"}) {
            Object candidate = response.get(key);
            if (candidate instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        rows.add(toStringKeyMap(map, requestedFields));
                    }
                }
                return applyWindow(rows, limit, offset);
            }
        }

        // Fallback to single report detail object.
        rows.add(toStringKeyMap(response, requestedFields));
        return applyWindow(rows, limit, offset);
    }

    private static List<Map<String, Object>> applyWindow(List<Map<String, Object>> rows, int limit, int offset) {
        if (rows.isEmpty()) {
            return rows;
        }
        int safeOffset = Math.max(offset, 0);
        if (safeOffset >= rows.size()) {
            return new ArrayList<>();
        }
        int toIndex = Math.min(rows.size(), safeOffset + Math.max(1, limit));
        return new ArrayList<>(rows.subList(safeOffset, toIndex));
    }

    private static String getFilterValue(List<Map<String, String>> filters, String key) {
        if (filters == null) {
            return null;
        }
        for (Map<String, String> filter : filters) {
            if (filter == null) continue;
            String value = filter.get(key);
            if (Utils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isNumeric(String value) {
        return Utils.isNotBlank(value) && value.matches("\\d+");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (Utils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> raw, List<String> requestedFields) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (requestedFields != null && !requestedFields.isEmpty()) {
            for (String field : requestedFields) {
                normalized.put(field, raw.get(field));
            }
            return normalized;
        }
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private Long resolveTotalRows(QueryRequest request, String subtype, List<Map<String, Object>> rows, int offset) {
        String countEndpoint = null;
        String countKey = "count";
        if (ZohodeskConstants.OBJECT_SUBTYPE_TICKETS.equals(subtype)) {
            countEndpoint = ZohodeskConstants.ENDPOINT_TICKETS_COUNT;
        } else if (ZohodeskConstants.OBJECT_SUBTYPE_DEPARTMENTS.equals(subtype)) {
            countEndpoint = ZohodeskConstants.ENDPOINT_DEPARTMENTS_COUNT;
        } else if (ZohodeskConstants.OBJECT_SUBTYPE_AGENTS.equals(subtype)) {
            countEndpoint = ZohodeskConstants.ENDPOINT_AGENTS_COUNT;
        }
        if (countEndpoint != null) {
            Long count = client.fetchCount(request.getConnectionConfig(), countEndpoint, true, countKey);
            if (count != null) {
                return count;
            }
        }
        if (rows == null) {
            return 0L;
        }
        return (long) (offset + rows.size());
    }
}
