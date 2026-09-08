package com.ovaledge.csp.tests.unit.apps.monetdb.main;

import com.ovaledge.csp.apps.monetdb.main.MonetDBConnector;
import com.ovaledge.csp.apps.monetdb.main.MonetDBProfilingService;
import com.ovaledge.csp.v3.core.apps.exceptions.ProfilingException;
import com.ovaledge.csp.v3.core.apps.exceptions.ProfilingUnsupportedException;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileBatchRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileBatchTarget;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileColumnRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileFieldSpec;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileRowCountRequest;
import com.ovaledge.csp.v3.core.apps.model.request.SampleProfileRequest;
import com.ovaledge.csp.v3.core.apps.model.response.ProfileBatchResponse;
import com.ovaledge.csp.v3.core.apps.model.response.ProfileColumnResult;
import com.ovaledge.csp.v3.core.apps.utils.ProfilingConstants;
import com.ovaledge.csp.v3.core.connectionpool.core.ConnectionResource;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MonetDB profiling. Soft-skip paths exercise production
 * {@link MonetDBProfilingService#profileColumn}; JDBC paths use an overridden
 * {@link MonetDBProfilingService#obtainJdbcResource}.
 */
class MonetDBProfilingServiceTest {

    @Test
    void connector_exposesProfilingService() {
        MonetDBConnector connector = new MonetDBConnector();
        assertNotNull(connector.getProfilingService());
        assertTrue(connector.getProfilingService() instanceof MonetDBProfilingService);
        assertFalse(connector.getProfilingService().getSkipDataTypesForProfile().isEmpty());
    }

    @Test
    void profileColumn_softSkipsUnsupportedDatatype_withoutThrowing() {
        MonetDBProfilingService service = new MonetDBProfilingService();
        ProfileColumnResult result = service.profileColumn(new ProfileColumnRequest(
                connectionConfig(), ObjectKind.ENTITY, "sys", "t", "c").withDataType("blob").withDataLength(10L));

        assertTrue(result.isUnsupportedDataTypeForProfile());
        assertEquals(0, result.getProfileStatus());
        assertEquals(-1, result.getEmptyCount());
        assertEquals(-1, result.getZeroCount());
        assertTrue(result.getMessage().contains(ProfilingConstants.SKIP_PROFILING_EXCEPT_NULLCOUNT_MESSAGE));
        assertTrue(result.getMessage().toLowerCase().contains("blob"));
        // No live JDBC: soft-skip still returns; non-null count falls back to 0
        assertEquals(0L, result.getNotNullCount());
    }

    @Test
    void profileColumn_lengthSkip_doesNotSetUnsupportedDatatypeFlag() {
        MonetDBProfilingService service = new MonetDBProfilingService();
        ProfileColumnResult result = service.profileColumn(new ProfileColumnRequest(
                connectionConfig(), ObjectKind.ENTITY, "sys", "t", "c")
                .withDataType("varchar").withDataLength(9000L));

        assertFalse(result.isUnsupportedDataTypeForProfile());
        assertEquals(0, result.getProfileStatus());
        assertEquals(-1, result.getEmptyCount());
        assertEquals(-1, result.getZeroCount());
        assertTrue(result.getMessage().contains(ProfilingConstants.SKIP_PROFILING_EXCEPT_NULLCOUNT_MESSAGE));
        assertTrue(result.getMessage().toLowerCase().contains("varchar"));
        assertEquals(0L, result.getNotNullCount());
    }

    @Test
    void profileColumn_negativeLengthSkip_usesNegativeLengthMessage() {
        MonetDBProfilingService service = new MonetDBProfilingService();
        ProfileColumnResult result = service.profileColumn(new ProfileColumnRequest(
                connectionConfig(), ObjectKind.ENTITY, "sys", "t", "c")
                .withDataType("varchar").withDataLength(-1L));

        assertFalse(result.isUnsupportedDataTypeForProfile());
        assertEquals(0, result.getProfileStatus());
        assertTrue(result.getMessage().contains(ProfilingConstants.SKIP_PROFILING_EXCEPT_NULLCOUNT_MESSAGE));
        assertTrue(result.getMessage().contains("-1") || result.getMessage().toLowerCase().contains("length"));
    }

    @Test
    void profileColumn_unsupportedKind_file_throwsUnsupported() {
        MonetDBProfilingService service = new MonetDBProfilingService();
        assertThrows(ProfilingUnsupportedException.class, () -> service.profileColumn(
                new ProfileColumnRequest(connectionConfig(), ObjectKind.FILE, "sys", "t", "c")));
    }

    @Test
    void getRowCount_missingIdentity_throwsProfilingException() {
        MonetDBProfilingService service = new MonetDBProfilingService();
        assertThrows(ProfilingException.class, () -> service.getRowCount(new ProfileRowCountRequest()));
    }

    @Test
    void getRowCount_withMockedJdbc_returnsCountAndUsesSchemaQualifiedSql() throws Exception {
        ConnectionResource resource = mock(ConnectionResource.class);
        when(resource.queryForObject(anyString(), eq("profileRowCount"), any(RowMapper.class)))
                .thenReturn(17L);
        MonetDBProfilingService service = serviceWithResource(resource);

        long count = service.getRowCount(new ProfileRowCountRequest(
                connectionConfig(), ObjectKind.ENTITY, "sys", "orders"));

        assertEquals(17L, count);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(resource).queryForObject(sqlCaptor.capture(), eq("profileRowCount"), any(RowMapper.class));
        assertEquals("SELECT COUNT(*) FROM \"sys\".\"orders\"", sqlCaptor.getValue());
    }

    @Test
    void profileColumn_withMockedJdbc_mapsAggregateRowAndTopValues() throws Exception {
        ConnectionResource resource = mock(ConnectionResource.class);
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("maxo", "z");
        agg.put("mino", "a");
        agg.put("distinctCount", 3L);
        agg.put("notNullCount", 10L);
        agg.put("emptyCount", 1L);
        agg.put("zeroCount", -1L);
        when(resource.queryForList(anyString(), eq("profileColumn"), eq(false)))
                .thenReturn(List.of(agg));
        Map<String, Object> top = new LinkedHashMap<>();
        top.put("val", "a");
        top.put("cnt", 5);
        when(resource.queryForList(anyString(), eq("profileTopValues"), eq(false)))
                .thenReturn(List.of(top));
        MonetDBProfilingService service = serviceWithResource(resource);

        ProfileColumnResult result = service.profileColumn(new ProfileColumnRequest(
                connectionConfig(), ObjectKind.ENTITY, "sys", "orders", "title")
                .withDataType("varchar").withDataLength(100L));

        assertEquals("a", result.getMinValue());
        assertEquals("z", result.getMaxValue());
        assertEquals(3L, result.getDistinctCount());
        assertEquals(10L, result.getNotNullCount());
        assertEquals(1L, result.getEmptyCount());
        assertTrue(result.getTopValuesJson().contains("\"a\":5"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(resource).queryForList(sqlCaptor.capture(), eq("profileColumn"), eq(false));
        assertTrue(sqlCaptor.getValue().contains("FROM \"sys\".\"orders\""));
        assertTrue(sqlCaptor.getValue().contains("\"title\""));
        assertFalse(sqlCaptor.getValue().contains(" = 0"),
                "varchar must not use numeric zeroCount SQL");
    }

    @Test
    void sampleProfile_withMockedJdbc_usesBareColumnSelectAndComputesStats() throws Exception {
        ConnectionResource resource = mock(ConnectionResource.class);
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("title", "Alpha");
        row1.put("amount", 10);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("title", "Beta");
        row2.put("amount", 20);
        when(resource.queryForList(anyString(), eq("sampleProfile"), anyBoolean()))
                .thenReturn(List.of(row1, row2));
        MonetDBProfilingService service = serviceWithResource(resource);

        SampleProfileRequest request = new SampleProfileRequest(
                connectionConfig(), ObjectKind.ENTITY, "sys", "orders")
                .withFields(List.of(
                        new ProfileFieldSpec("title", "varchar", 50L),
                        new ProfileFieldSpec("amount", "integer", null)));
        request.setSampleSize(10);
        request.setSamplePageSize(10);

        Map<String, ProfileColumnResult> results = service.sampleProfile(request);

        assertEquals(2, results.get("title").getDistinctCount());
        assertEquals("Alpha", results.get("title").getMinValue());
        assertEquals("Beta", results.get("title").getMaxValue());
        assertEquals(2, results.get("title").getTotalRowCount());
        assertEquals(2, results.get("amount").getDistinctCount());
        assertEquals(2, results.get("amount").getTotalRowCount());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(resource).queryForList(sqlCaptor.capture(), eq("sampleProfile"), eq(false));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.startsWith("SELECT "));
        assertTrue(sql.contains("FROM \"sys\".\"orders\""));
        assertTrue(sql.contains("SUBSTRING(\"title\", 1,"));
        assertTrue(sql.contains("\"amount\""));
        assertFalse(sql.contains("\"orders\".\"title\""),
                "must not qualify columns as table.col against schema.table FROM");
        assertFalse(sql.contains("\"orders\".\"amount\""));
    }

    @Test
    void sampleAndBatch_mockedSuccessPaths() {
        MonetDBProfilingService service = new MonetDBProfilingService() {
            @Override
            public long getRowCount(ProfileRowCountRequest request) {
                return 42L;
            }

            @Override
            public ProfileColumnResult profileColumn(ProfileColumnRequest request) {
                ProfileColumnResult r = new ProfileColumnResult();
                r.setDistinctCount(3);
                r.setNotNullCount(10);
                r.setMinValue("a");
                r.setMaxValue("z");
                r.setTopValuesJson("{\"a\":5}");
                return r;
            }

            @Override
            public Map<String, ProfileColumnResult> sampleProfile(SampleProfileRequest request) {
                Map<String, ProfileColumnResult> map = new LinkedHashMap<>();
                ProfileColumnResult r = new ProfileColumnResult();
                // Successful sample leaves status 0; status 1 is SQL→sample fallback only
                r.setDistinctCount(2);
                map.put("col1", r);
                return map;
            }
        };

        assertEquals(42L, service.getRowCount(new ProfileRowCountRequest(
                connectionConfig(), ObjectKind.ENTITY, "sys", "t")));

        ProfileColumnResult col = service.profileColumn(new ProfileColumnRequest(
                connectionConfig(), ObjectKind.ENTITY, "sys", "t", "col1").withDataType("varchar"));
        assertEquals(3, col.getDistinctCount());
        assertNotNull(col.getTopValuesJson());

        Map<String, ProfileColumnResult> sample = service.sampleProfile(
                new SampleProfileRequest(connectionConfig(), ObjectKind.ENTITY, "sys", "t"));
        assertEquals(0, sample.get("col1").getProfileStatus());
        assertEquals(2, sample.get("col1").getDistinctCount());

        ProfileBatchResponse batch = service.profileBatch(new ProfileBatchRequest(
                connectionConfig(), ObjectKind.ENTITY, "sys")
                .withBatchMode(ProfilingConstants.BATCH_MODE_BOTH)
                .withTargets(List.of(new ProfileBatchTarget("t", List.of(
                        new ProfileFieldSpec("col1", "varchar", 100L))))));
        assertEquals(42L, batch.getRowCounts().get("t"));
        assertTrue(batch.getColumnResults().get("t").containsKey("col1"));
        assertTrue(batch.getFailedObjectsWithReason().isEmpty());
    }

    private static MonetDBProfilingService serviceWithResource(ConnectionResource resource) {
        return new MonetDBProfilingService() {
            @Override
            protected ConnectionResource obtainJdbcResource(ConnectionConfig config) {
                return resource;
            }
        };
    }

    private static ConnectionConfig connectionConfig() {
        return new ConnectionConfig()
                .withServerType("monetdb")
                .withAdditionalAttribute("host", "localhost")
                .withAdditionalAttribute("database", "demo");
    }
}
