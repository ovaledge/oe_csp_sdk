package com.ovaledge.csp.apps.monetdb.main;

import com.ovaledge.csp.v3.core.apps.model.request.QueryRequest;
import com.ovaledge.csp.v3.core.apps.model.response.QueryResponse;
import com.ovaledge.csp.v3.core.apps.service.QueryService;
import com.ovaledge.csp.v3.core.connectionpool.core.ConnectionPoolManager;
import com.ovaledge.csp.v3.core.connectionpool.core.ConnectionResource;
import com.ovaledge.csp.v3.core.connectionpool.enums.ResourceType;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Query service for MonetDB: fetches data from a table/view with limit/offset.
 * Uses ConnectionConfig as-is. No Client; JDBC operations via ConnectionPoolManager.
 */
public class MonetDBQueryService implements QueryService {

    private static final Logger LOG = LoggerFactory.getLogger(MonetDBQueryService.class);

    private static final RowMapper<Long> COUNT_ROW_MAPPER = (ResultSet rs, int rowNum) -> rs.getLong(1);

    @Override
    /**
     * Execute a fetch request for the specified table/view (entity).
     *
     * Responsibilities:
     * - Validate request (containerId, entityId, connection config)
     * - Resolve pagination (limit/offset) and requested fields
     * - Use the ConnectionPoolManager to run a safe SELECT with proper quoting
     * - Return rows, total row count and success status in the QueryResponse
     *
     * @param request query request containing containerId, entityId, fields, limit and offset
     * @return QueryResponse containing the result rows and totalRows
     * @throws Exception any JDBC or connection error bubbling up
     */
    public QueryResponse fetchData(QueryRequest request) throws Exception {
        String containerId = request.getContainerId();
        String entityId = request.getEntityId();
        if (containerId == null || containerId.isBlank() || entityId == null || entityId.isBlank()) {
            return new QueryResponse().withData(new ArrayList<>()).withTotalRows(0).withSuccess(false);
        }
        ConnectionConfig config = request.getConnectionConfig();
        if (config == null) {
            return new QueryResponse().withData(new ArrayList<>()).withTotalRows(0).withSuccess(false);
        }
        int limit = request.getLimit() != null && request.getLimit() > 0 ? request.getLimit() : 1000;
        int offset = request.getOffset() != null && request.getOffset() >= 0 ? request.getOffset() : 0;
        List<String> fields = request.getFields() != null && !request.getFields().isEmpty() ? request.getFields() : null;
        try {
            config = MonetDBConnector.ensureConnectionConfig(config);
            ConnectionResource resource = (ConnectionResource) ConnectionPoolManager.getInstance()
                    .getOrCreateResource(config, ResourceType.JDBC);
            String quotedTable = quote(containerId) + "." + quote(entityId);
            String colList = fields != null && !fields.isEmpty()
                    ? fields.stream().map(MonetDBQueryService::quote).collect(Collectors.joining(","))
                    : "*";
            String sql = "SELECT " + colList + " FROM " + quotedTable + " LIMIT ? OFFSET ?";
            List<Map<String, Object>> data = resource.queryForList(sql, "fetchData", false, limit, offset);
            Long total = resource.queryForObject("SELECT COUNT(*) FROM " + quotedTable, "fetchDataCount", COUNT_ROW_MAPPER);
            int totalInt = (total != null && total <= Integer.MAX_VALUE) ? total.intValue() : Integer.MAX_VALUE;
            return new QueryResponse()
                    .withData(data != null ? data : new ArrayList<>())
                    .withTotalRows(totalInt)
                    .withSuccess(true);
        } catch (Exception e) {
            LOG.warn("MonetDB fetchData failed: {}", e.getMessage());
            return new QueryResponse().withData(new ArrayList<>()).withTotalRows(0).withSuccess(false);
        }
    }

    private static String quote(String name) {
        if (name == null) return "\"\"";
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
