package com.ovaledge.csp.apps.monetdb.main;

import com.ovaledge.csp.v3.core.apps.exceptions.ProfilingException;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileColumnRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileFieldSpec;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileRowCountRequest;
import com.ovaledge.csp.v3.core.apps.model.request.SampleProfileRequest;
import com.ovaledge.csp.v3.core.apps.model.response.ProfileColumnResult;
import com.ovaledge.csp.v3.core.apps.service.AbstractProfilingService;
import com.ovaledge.csp.v3.core.apps.utils.JdbcProfilingSqlUtils;
import com.ovaledge.csp.v3.core.apps.utils.LogUtils;
import com.ovaledge.csp.v3.core.apps.utils.ProfileStatsCalculator;
import com.ovaledge.csp.v3.core.apps.utils.ProfilingConstants;
import com.ovaledge.csp.v3.core.connectionpool.core.ConnectionPoolManager;
import com.ovaledge.csp.v3.core.connectionpool.core.ConnectionResource;
import com.ovaledge.csp.v3.core.connectionpool.enums.ResourceType;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

    /**
     * MonetDB full profiling: row count, aggregate column, and sample for {@code ENTITY}/{@code VIEW}.
     * {@code FILE} and other kinds throw {@link com.ovaledge.csp.v3.core.apps.exceptions.ProfilingUnsupportedException}.
     * Soft-skip messaging and sample page-merge match legacy DbDto / PostgreSqlDto behavior.
     */
public class MonetDBProfilingService extends AbstractProfilingService {

    private static final Logger LOG = LoggerFactory.getLogger(MonetDBProfilingService.class);

    private static final List<String> SKIP_TYPES = Collections.unmodifiableList(Arrays.asList(
            "blob", "clob", "json", "uuid", "geometry", "inet", "url", "xml"
    ));

    private static final List<String> LENGTH_EXEMPT_TYPES = Collections.unmodifiableList(Arrays.asList(
            "text", "clob", "string"
    ));

    private static final RowMapper<Long> LONG_MAPPER = (ResultSet rs, int rowNum) -> rs.getLong(1);

    @Override
    public List<String> getSkipDataTypesForProfile() {
        return SKIP_TYPES;
    }

    @Override
    public long getRowCount(ProfileRowCountRequest request) {
        requireObjectKind(request, ObjectKind.ENTITY, ObjectKind.VIEW);
        validateIdentity(request.getConnectionConfig(), request.getContainerId(), request.getEntityId());
        logCorrelation(request.getJobStepId(), request.isPartition(), request.getContainerId(), request.getEntityId());
        try {
            ConnectionConfig config = MonetDBConnector.ensureConnectionConfig(request.getConnectionConfig());
            ConnectionResource resource = obtainJdbcResource(config);
            String sql = JdbcProfilingSqlUtils.buildRowCountSql(
                    request.getContainerId(), request.getEntityId(), request.getAdvancedDataQuery());
            Long count = resource.queryForObject(sql, "profileRowCount", LONG_MAPPER);
            return count != null ? count : 0L;
        } catch (Exception e) {
            LOG.error("MonetDB getRowCount failed for {}.{}: {}",
                    request.getContainerId(), request.getEntityId(), LogUtils.getStackTrace(e));
            throw new ProfilingException("Failed to get row count: " + e.getMessage(), e);
        }
    }

    @Override
    public ProfileColumnResult profileColumn(ProfileColumnRequest request) {
        requireObjectKind(request, ObjectKind.ENTITY, ObjectKind.VIEW);
        validateIdentity(request.getConnectionConfig(), request.getContainerId(), request.getEntityId());
        if (request.getFieldName() == null || request.getFieldName().isBlank()) {
            throw new ProfilingException("fieldName is required for profileColumn");
        }
        logCorrelation(request.getJobStepId(), request.isPartition(), request.getContainerId(), request.getEntityId());

        String dataType = request.getDataType();
        long maxLen = request.getMaxColumnProfileLength();
        Long dataLength = request.getDataLength();
        boolean skipType = JdbcProfilingSqlUtils.isSkippedDataType(dataType, getSkipDataTypesForProfile());
        boolean skipLength = JdbcProfilingSqlUtils.exceedsMaxLength(
                dataLength, maxLen, dataType, LENGTH_EXEMPT_TYPES);
        if (skipType || skipLength) {
            return buildSkipResult(request, skipType, skipLength);
        }
        try {
            ConnectionConfig config = MonetDBConnector.ensureConnectionConfig(request.getConnectionConfig());
            ConnectionResource resource = obtainJdbcResource(config);
            boolean stringType = JdbcProfilingSqlUtils.isStringDataType(dataType);
            boolean numericType = JdbcProfilingSqlUtils.isNumericDataType(dataType);
            String sql = JdbcProfilingSqlUtils.buildAggregateProfileSql(
                    request.getContainerId(), request.getEntityId(), request.getFieldName(),
                    dataType, stringType, numericType, request.getAdvancedDataQuery());
            List<Map<String, Object>> rows = resource.queryForList(sql, "profileColumn", false);
            ProfileColumnResult result = new ProfileColumnResult();
            if (rows != null && !rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                result.setMaxValue(asString(row.get("maxo")));
                result.setMinValue(asString(row.get("mino")));
                result.setDistinctCount(asLong(row.get("distinctCount")));
                result.setNotNullCount(asLong(row.get("notNullCount")));
                result.setEmptyCount(asLong(row.get("emptyCount")));
                result.setZeroCount(asLong(row.get("zeroCount")));
            }
            result.setTopValuesJson(fetchTopValuesJson(resource, request));
            return result;
        } catch (ProfilingException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("MonetDB profileColumn failed for {}.{}.{}: {}",
                    request.getContainerId(), request.getEntityId(), request.getFieldName(), e.getMessage());
            throw new ProfilingException("Failed to profile column: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, ProfileColumnResult> sampleProfile(SampleProfileRequest request) {
        requireObjectKind(request, ObjectKind.ENTITY, ObjectKind.VIEW);
        validateIdentity(request.getConnectionConfig(), request.getContainerId(), request.getEntityId());
        logCorrelation(request.getJobStepId(), request.isPartition(), request.getContainerId(), request.getEntityId());

        int sampleSize = request.getSampleSize() != null && request.getSampleSize() > 0
                ? request.getSampleSize() : ProfilingConstants.DEFAULT_SAMPLE_PAGE_SIZE;
        int pageSize = request.getSamplePageSize() != null && request.getSamplePageSize() > 0
                ? request.getSamplePageSize() : ProfilingConstants.DEFAULT_SAMPLE_PAGE_SIZE;

        Map<String, ProfileFieldSpec> fieldSpecs = resolveFieldSpecs(request);
        Map<String, String> typeByField = new LinkedHashMap<>();
        List<String> selectableColumns = new ArrayList<>();
        Map<String, ProfileColumnResult> accumulated = new LinkedHashMap<>();

        ConnectionConfig config = MonetDBConnector.ensureConnectionConfig(request.getConnectionConfig());
        ConnectionResource resource;
        try {
            resource = obtainJdbcResource(config);
        } catch (Exception e) {
            throw new ProfilingException("Failed to obtain JDBC resource for sample profile: " + e.getMessage(), e);
        }

        for (Map.Entry<String, ProfileFieldSpec> entry : fieldSpecs.entrySet()) {
            String name = entry.getKey();
            ProfileFieldSpec spec = entry.getValue();
            String type = spec.getDataType() != null ? spec.getDataType() : "varchar";
            typeByField.put(name, type);
            if (JdbcProfilingSqlUtils.isSkippedDataType(type, getSkipDataTypesForProfile())) {
                accumulated.put(name, buildSkippedSampleResult(resource, request, name, type));
            } else {
                selectableColumns.add(buildSampleColumnExpression(
                        name, type, request.getMaxColumnProfileLength()));
            }
        }

        if (selectableColumns.isEmpty() && !fieldSpecs.isEmpty()) {
            LOG.warn("No supported data type columns to sample-profile for {}.{}",
                    request.getContainerId(), request.getEntityId());
            return accumulated;
        }

        Map<String, Map<String, Object>> priorPageMetrics = new HashMap<>();
        Map<String, Long> firstPageDistinct = new HashMap<>();
        try {
            int fetched = 0;
            int offset = 0;
            int pageIndex = 0;
            String selectList = selectableColumns.isEmpty() ? null : String.join(", ", selectableColumns);
            while (fetched < sampleSize) {
                int limit = Math.min(pageSize, sampleSize - fetched);
                String sql = JdbcProfilingSqlUtils.buildSampleSql(
                        request.getContainerId(), request.getEntityId(), selectList,
                        request.getAdvancedSampleClause(), limit, offset);
                List<Map<String, Object>> rows = resource.queryForList(sql, "sampleProfile", false);
                if (rows == null || rows.isEmpty()) {
                    break;
                }
                Map<String, List<Object>> byColumn = ProfileStatsCalculator.rearrangeRowToColumnData(rows);
                Map<String, ProfileColumnResult> page = new LinkedHashMap<>();
                for (Map.Entry<String, List<Object>> colEntry : byColumn.entrySet()) {
                    String colName = colEntry.getKey();
                    if (!selectableColumns.isEmpty() && typeByField.containsKey(colName)
                            && JdbcProfilingSqlUtils.isSkippedDataType(
                            typeByField.get(colName), getSkipDataTypesForProfile())) {
                        continue;
                    }
                    if (request.getFieldNames() != null && !request.getFieldNames().isEmpty()
                            && request.getFieldNames().stream().noneMatch(f -> f.equalsIgnoreCase(colName))) {
                        continue;
                    }
                    String type = typeByField.getOrDefault(colName, "varchar");
                    ProfileColumnResult stats = ProfileStatsCalculator.computeStatsForColumn(type, colEntry.getValue());
                    // Legacy ProfileTable.profileTable sets totalRowCount to the page row count before merge
                    stats.setTotalRowCount(rows.size());
                    page.put(colName, stats);
                    typeByField.putIfAbsent(colName, type);
                }
                ProfileStatsCalculator.mergeSamplePage(
                        accumulated, page, priorPageMetrics, pageIndex, firstPageDistinct, typeByField);
                fetched += rows.size();
                offset += rows.size();
                pageIndex++;
                if (rows.size() < limit) {
                    break;
                }
            }
            return accumulated;
        } catch (ProfilingException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("MonetDB sampleProfile failed for {}.{}: {}",
                    request.getContainerId(), request.getEntityId(), e.getMessage());
            throw new ProfilingException("Failed to sample profile: " + e.getMessage(), e);
        }
    }

    private ProfileColumnResult buildSkipResult(ProfileColumnRequest request,
                                                boolean skipDatatype, boolean skipLength) {
        String columnRef = request.getEntityId() + "." + request.getFieldName();
        String type = request.getDataType();
        String base;
        if (skipDatatype) {
            base = ProfilingConstants.skipDatatypeMessage(type) + "for column: " + columnRef;
        } else if (request.getDataLength() != null && request.getDataLength() < 0) {
            base = ProfilingConstants.skipNegativeLengthMessage(type, request.getDataLength())
                    + " for column: " + columnRef;
        } else {
            base = ProfilingConstants.skipLengthMessage(type, request.getMaxColumnProfileLength())
                    + " for column: " + columnRef;
        }
        String message = ProfilingConstants.withSkipExceptNullCount(base);

        ProfileColumnResult result = new ProfileColumnResult();
        // Legacy MySQL skip: unsupported flag only for datatype skip; profileStatus stays 0 so
        // Oasis persists nullcount as rowcount - notNullCount.
        result.setUnsupportedDataTypeForProfile(skipDatatype);
        result.setMessage(message);
        result.setEmptyCount(-1);
        result.setZeroCount(-1);
        LOG.warn(message);
        result.setNotNullCount(fetchNonNullCount(request));
        return result;
    }

    private ProfileColumnResult buildSkippedSampleResult(ConnectionResource resource,
                                                         SampleProfileRequest request,
                                                         String fieldName, String dataType) {
        String columnRef = request.getEntityId() + "." + fieldName;
        String base = ProfilingConstants.skipDatatypeMessage(dataType) + "for column: " + columnRef;
        String message = ProfilingConstants.withSkipExceptNullCount(base);
        ProfileColumnResult pf = new ProfileColumnResult();
        pf.setUnsupportedDataTypeForProfile(true);
        pf.setMessage(message);
        pf.setEmptyCount(-1);
        pf.setZeroCount(-1);
        LOG.warn(message);
        try {
            String sql = JdbcProfilingSqlUtils.buildNonNullCountSql(
                    request.getContainerId(), request.getEntityId(), fieldName, request.getRowCountLimit());
            Long notNull = resource.queryForObject(sql, "profileNonNullCount", LONG_MAPPER);
            pf.setNotNullCount(notNull != null ? notNull : 0L);
        } catch (Exception ex) {
            LOG.warn("Could not compute sample null count for {}: {}", fieldName, ex.getMessage());
        }
        return pf;
    }

    private long fetchNonNullCount(ProfileColumnRequest request) {
        try {
            ConnectionConfig config = MonetDBConnector.ensureConnectionConfig(request.getConnectionConfig());
            ConnectionResource resource = obtainJdbcResource(config);
            String sql = JdbcProfilingSqlUtils.buildNonNullCountSql(
                    request.getContainerId(), request.getEntityId(), request.getFieldName(),
                    request.getRowCountLimit());
            Long notNull = resource.queryForObject(sql, "profileNonNullCount", LONG_MAPPER);
            return notNull != null ? notNull : 0L;
        } catch (Exception e) {
            LOG.warn("MonetDB non-null count for skip path failed: {}", e.getMessage());
            return 0L;
        }
    }

    private String fetchTopValuesJson(ConnectionResource resource, ProfileColumnRequest request) {
        try {
            String sql = JdbcProfilingSqlUtils.buildTopValuesSql(
                    request.getContainerId(), request.getEntityId(), request.getFieldName(),
                    request.getTopValuesLimit(), request.getAdvancedDataQuery());
            List<Map<String, Object>> rows = resource.queryForList(sql, "profileTopValues", false);
            Map<String, Integer> top = new LinkedHashMap<>();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Object val = row.get("val");
                    Object cnt = row.get("cnt");
                    if (val != null) {
                        top.put(val.toString(), (int) asLong(cnt));
                    }
                }
            }
            return top.entrySet().stream()
                    .map(e -> "\"" + ProfilingConstants.escapeJson(e.getKey()) + "\":" + e.getValue())
                    .collect(Collectors.joining(",", "{", "}"));
        } catch (Exception e) {
            LOG.warn("MonetDB top values failed for {}: {}", request.getFieldName(), e.getMessage());
            return "{}";
        }
    }

    private static Map<String, ProfileFieldSpec> resolveFieldSpecs(SampleProfileRequest request) {
        Map<String, ProfileFieldSpec> specs = new LinkedHashMap<>();
        if (request.getFields() != null) {
            for (ProfileFieldSpec field : request.getFields()) {
                if (field != null && field.getFieldName() != null && !field.getFieldName().isBlank()) {
                    specs.put(field.getFieldName(), field);
                }
            }
        }
        if (specs.isEmpty() && request.getFieldNames() != null) {
            for (String name : request.getFieldNames()) {
                if (name != null && !name.isBlank()) {
                    specs.put(name, new ProfileFieldSpec(name, "varchar", null));
                }
            }
        }
        return specs;
    }

    /**
     * Build SELECT expression using bare quoted column names (valid with
     * {@code FROM "schema"."table"}). Do not qualify as {@code "table"."col"} —
     * that fails on Postgres-style engines without a matching range variable.
     */
    private static String buildSampleColumnExpression(String column, String dataType, long maxLen) {
        String qCol = JdbcProfilingSqlUtils.quoteIdentifier(column);
        if (JdbcProfilingSqlUtils.isStringDataType(dataType)) {
            long len = maxLen > 0 ? maxLen : ProfilingConstants.DEFAULT_MAX_COLUMN_PROFILE_LENGTH;
            return "SUBSTRING(" + qCol + ", 1, " + len + ") AS " + qCol;
        }
        return qCol;
    }

    /**
     * JDBC resource lookup; protected override point for unit tests.
     */
    protected ConnectionResource obtainJdbcResource(ConnectionConfig config) {
        return (ConnectionResource) ConnectionPoolManager.getInstance()
                .getOrCreateResource(config, ResourceType.JDBC);
    }

    private static void logCorrelation(Integer jobStepId, boolean partition, String containerId, String entityId) {
        if (jobStepId != null || partition) {
            LOG.debug("Profiling correlation jobStepId={}, partition={}, object={}.{}",
                    jobStepId, partition, containerId, entityId);
        }
    }

    private static void validateIdentity(ConnectionConfig config, String containerId, String entityId) {
        if (config == null) {
            throw new ProfilingException("connectionConfig is required");
        }
        if (containerId == null || containerId.isBlank() || entityId == null || entityId.isBlank()) {
            throw new ProfilingException("containerId and entityId are required");
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
