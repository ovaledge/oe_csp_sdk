package com.ovaledge.csp.apps.awsconsole.main;

import com.ovaledge.csp.apps.awsconsole.constants.AwsConsoleConstants;
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
 * Unit tests for AWS Console connector: attributes, supported objects, and connection validation.
 * Does not require valid AWS credentials or network.
 */
class AwsConsoleConnectorTest {

    private AwsConsoleConnector connector;

    @BeforeEach
    void setUp() {
        connector = new AwsConsoleConnector();
    }

    @Test
    void getServerType_returnsAwsConsole() {
        assertEquals(AwsConsoleConstants.SERVER_TYPE, connector.getServerType());
    }

    @Test
    void getAttributes_containsRequiredKeys() {
        Map<String, ConnectionAttribute> attrs = connector.getAttributes();
        assertNotNull(attrs);
        assertTrue(attrs.containsKey(AwsConsoleConstants.KEY_ACCESS_KEY_ID));
        assertTrue(attrs.containsKey(AwsConsoleConstants.KEY_SECRET_ACCESS_KEY));
        assertTrue(attrs.containsKey(AwsConsoleConstants.KEY_REGION));
        assertTrue(attrs.get(AwsConsoleConstants.KEY_ACCESS_KEY_ID).isRequired());
        assertTrue(attrs.get(AwsConsoleConstants.KEY_SECRET_ACCESS_KEY).isRequired());
    }

    @Test
    void getSupportedObjects_returnsExpectedTypes() {
        SupportedObjectsResponse response = connector.getMetadataService().getSupportedObjects();
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getSupportedObjects());
        assertFalse(response.getSupportedObjects().isEmpty());
        assertTrue(response.getSupportedObjects().stream()
                .anyMatch(o -> AwsConsoleConstants.DISPLAY_NAME_EC2_INSTANCES.equals(o.getDisplayName())));
        assertTrue(response.getSupportedObjects().stream()
                .anyMatch(o -> AwsConsoleConstants.DISPLAY_NAME_S3_BUCKETS.equals(o.getDisplayName())));
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
    void validateConnection_missingAccessKey_returnsFailure() throws Exception {
        ConnectionConfig config = ConnectionConfig.builder()
                .withConnectionInfoId(1)
                .withPassword("secret")
                .build();

        ValidateConnectionResponse response = connector.validateConnection(config);
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains(AwsConsoleConstants.MSG_MISSING_ACCESS_KEY)
                || response.getMessage().toLowerCase().contains("access"));
    }

    @Test
    void validateConnection_missingSecretKey_returnsFailure() throws Exception {
        ConnectionConfig config = ConnectionConfig.builder()
                .withConnectionInfoId(1)
                .withUsername("accessKey")
                .build();

        ValidateConnectionResponse response = connector.validateConnection(config);
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains(AwsConsoleConstants.MSG_MISSING_SECRET_KEY)
                || response.getMessage().toLowerCase().contains("secret"));
    }

    @Test
    void exchangeAttributes_roundTrip_preservesConnectorSpecificValues() {
        Map<String, ConnectionAttribute> attrs = connector.getAttributes();
        attrs.get(AwsConsoleConstants.KEY_ACCESS_KEY_ID).setValue("AKIAIOSFODNN7EXAMPLE");
        attrs.get(AwsConsoleConstants.KEY_SECRET_ACCESS_KEY).setValue("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        attrs.get(AwsConsoleConstants.KEY_REGION).setValue("us-west-2");
        if (attrs.containsKey(SdkCoreConstants.CONNECTION_ATTR_CONN_TYPE)) {
            attrs.get(SdkCoreConstants.CONNECTION_ATTR_CONN_TYPE).setValue("20");
        }
        if (attrs.containsKey(SdkCoreConstants.CONN_ATTR_BRIDGEID)) {
            attrs.get(SdkCoreConstants.CONN_ATTR_BRIDGEID).setValue("0");
        }

        ConnInfo connInfo = connector.exchangeAttributes(attrs);
        assertNotNull(connInfo);
        assertEquals(AwsConsoleConstants.SERVER_TYPE, connInfo.getServertype());

        Map<String, ConnectionAttribute> roundTrip = connector.exchangeAttributes(connInfo);
        assertNotNull(roundTrip);
        assertEquals("AKIAIOSFODNN7EXAMPLE", roundTrip.get(AwsConsoleConstants.KEY_ACCESS_KEY_ID).getValue());
        assertEquals("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                roundTrip.get(AwsConsoleConstants.KEY_SECRET_ACCESS_KEY).getValue());
        assertEquals("us-west-2", roundTrip.get(AwsConsoleConstants.KEY_REGION).getValue());
    }
}
