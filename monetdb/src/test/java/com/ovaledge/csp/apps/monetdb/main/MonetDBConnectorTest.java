package com.ovaledge.csp.apps.monetdb.main;

import com.ovaledge.csp.apps.monetdb.constants.MonetDBConstants;
import com.ovaledge.csp.dto.model.ConnInfo;
import com.ovaledge.csp.sdk.core.SdkCoreConstants;
import com.ovaledge.csp.dto.model.ConnectionAttribute;
import com.ovaledge.csp.v3.core.apps.model.response.SupportedObjectsResponse;
import com.ovaledge.csp.v3.core.apps.model.response.ValidateConnectionResponse;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MonetDB connector: attributes, supported objects, and connection validation.
 * Does not require a running MonetDB instance.
 */
class MonetDBConnectorTest {

    private MonetDBConnector connector;

    @BeforeEach
    void setUp() {
        connector = new MonetDBConnector();
    }

    @Test
    void getServerType_returnsMonetDb() {
        assertEquals(MonetDBConstants.SERVER_TYPE, connector.getServerType());
    }

    @Test
    void getAttributes_containsRequiredKeys() {
        Map<String, ConnectionAttribute> attrs = connector.getAttributes();
        assertNotNull(attrs);
        assertTrue(attrs.containsKey(MonetDBConstants.KEY_HOST));
        assertTrue(attrs.containsKey(MonetDBConstants.KEY_PORT));
        assertTrue(attrs.containsKey(MonetDBConstants.KEY_DATABASE));
        assertTrue(attrs.containsKey(MonetDBConstants.KEY_USERNAME));
        assertTrue(attrs.containsKey(MonetDBConstants.KEY_PASSWORD));
        assertTrue(attrs.containsKey(MonetDBConstants.KEY_JDBC_DRIVER));
        assertEquals(ConnectionAttribute.Type.HIDDEN, attrs.get(MonetDBConstants.KEY_JDBC_DRIVER).getType());
        assertEquals(MonetDBConstants.JDBC_DRIVER_CLASS, attrs.get(MonetDBConstants.KEY_JDBC_DRIVER).getValue());
        assertTrue(attrs.get(MonetDBConstants.KEY_HOST).isRequired());
        assertTrue(attrs.get(MonetDBConstants.KEY_DATABASE).isRequired());
    }

    @Test
    void getSupportedObjects_returnsExpectedTypes() {
        SupportedObjectsResponse response = connector.getMetadataService().getSupportedObjects();
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getSupportedObjects());
        assertFalse(response.getSupportedObjects().isEmpty());

        long entityCount = response.getSupportedObjects().stream()
                .filter(o -> MonetDBConstants.DISPLAY_NAME_TABLES.equals(o.getDisplayName())
                        || MonetDBConstants.DISPLAY_NAME_VIEWS.equals(o.getDisplayName()))
                .count();
        assertTrue(entityCount >= 2, "Expected at least Tables and Views");
        assertTrue(response.getSupportedObjects().stream()
                .anyMatch(o -> MonetDBConstants.DISPLAY_NAME_FUNCTIONS.equals(o.getDisplayName())));
        assertTrue(response.getSupportedObjects().stream()
                .anyMatch(o -> MonetDBConstants.DISPLAY_NAME_SEQUENCES.equals(o.getDisplayName())));
        assertTrue(response.getSupportedObjects().stream()
                .anyMatch(o -> MonetDBConstants.DISPLAY_NAME_INDEXES.equals(o.getDisplayName())));
        assertTrue(response.getSupportedObjects().stream()
                .anyMatch(o -> MonetDBConstants.DISPLAY_NAME_TRIGGERS.equals(o.getDisplayName())));
    }

    @Test
    void validateConnection_nullConfig_returnsFailure() throws Exception {
        ValidateConnectionResponse response = connector.validateConnection(null);
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().toLowerCase().contains("invalid"));
    }

    @Test
    void validateConnection_missingHost_returnsFailure() throws Exception {
        ConnectionConfig config = ConnectionConfig.builder()
                .withConnectionInfoId(1)
                .withDatabase("mydb")
                .withUsername("u")
                .withPassword("p")
                .build();

        ValidateConnectionResponse response = connector.validateConnection(config);
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains(MonetDBConstants.MSG_MISSING_HOST)
                || response.getMessage().toLowerCase().contains("host"));
    }

    @Test
    void validateConnection_missingDatabase_returnsFailure() throws Exception {
        ConnectionConfig config = ConnectionConfig.builder()
                .withConnectionInfoId(1)
                .withHost("localhost")
                .withUsername("u")
                .withPassword("p")
                .build();

        ValidateConnectionResponse response = connector.validateConnection(config);
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains(MonetDBConstants.MSG_MISSING_DATABASE)
                || response.getMessage().toLowerCase().contains("database"));
    }

    @Test
    void exchangeAttributes_roundTrip_preservesConnectorSpecificValues() {
        Map<String, ConnectionAttribute> attrs = connector.getAttributes();
        attrs.get(MonetDBConstants.KEY_HOST).setValue("testhost");
        attrs.get(MonetDBConstants.KEY_PORT).setValue("50000");
        attrs.get(MonetDBConstants.KEY_DATABASE).setValue("testdb");
        attrs.get(MonetDBConstants.KEY_USERNAME).setValue("testuser");
        attrs.get(MonetDBConstants.KEY_PASSWORD).setValue("testpass");
        if (attrs.containsKey(SdkCoreConstants.CONNECTION_ATTR_CONN_TYPE)) {
            attrs.get(SdkCoreConstants.CONNECTION_ATTR_CONN_TYPE).setValue("20");
        }
        if (attrs.containsKey(SdkCoreConstants.CONN_ATTR_BRIDGEID)) {
            attrs.get(SdkCoreConstants.CONN_ATTR_BRIDGEID).setValue("0");
        }

        ConnInfo connInfo = connector.exchangeAttributes(attrs);
        assertNotNull(connInfo);
        assertEquals(MonetDBConstants.SERVER_TYPE, connInfo.getServertype());

        Map<String, ConnectionAttribute> roundTrip = connector.exchangeAttributes(connInfo);
        assertNotNull(roundTrip);
        assertEquals("testhost", roundTrip.get(MonetDBConstants.KEY_HOST).getValue());
        assertEquals("50000", roundTrip.get(MonetDBConstants.KEY_PORT).getValue());
        assertEquals("testdb", roundTrip.get(MonetDBConstants.KEY_DATABASE).getValue());
        assertEquals("testuser", roundTrip.get(MonetDBConstants.KEY_USERNAME).getValue());
        assertEquals("testpass", roundTrip.get(MonetDBConstants.KEY_PASSWORD).getValue());
    }
}
