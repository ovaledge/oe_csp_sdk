package com.ovaledge.csp.apps.zohodesk.main;

import com.ovaledge.csp.apps.zohodesk.constants.ZohodeskConstants;
import com.ovaledge.csp.dto.model.ConnInfo;
import com.ovaledge.csp.dto.model.ConnectionAttribute;
import com.ovaledge.csp.v3.core.apps.model.response.ValidateConnectionResponse;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZohodeskConnectorTest {

    private ZohodeskConnector connector;

    @BeforeEach
    void setUp() {
        connector = new ZohodeskConnector();
    }

    @Test
    void getServerType_returnsZohoDesk() {
        assertEquals(ZohodeskConstants.SERVER_TYPE, connector.getServerType());
    }

    @Test
    void getAttributes_containsRequiredKeys() {
        Map<String, ConnectionAttribute> attrs = connector.getAttributes();
        assertNotNull(attrs);
        assertTrue(attrs.containsKey(ZohodeskConstants.KEY_BASE_URL));
        assertTrue(attrs.containsKey(ZohodeskConstants.KEY_ACCOUNTS_URL));
        assertTrue(attrs.containsKey(ZohodeskConstants.KEY_ORG_ID));
        assertTrue(attrs.containsKey(ZohodeskConstants.KEY_CLIENT_ID));
        assertTrue(attrs.containsKey(ZohodeskConstants.KEY_CLIENT_SECRET));
        assertTrue(attrs.containsKey(ZohodeskConstants.KEY_REFRESH_TOKEN));
        assertTrue(attrs.get(ZohodeskConstants.KEY_CLIENT_SECRET).isMasked());
        assertTrue(attrs.get(ZohodeskConstants.KEY_REFRESH_TOKEN).isMasked());
    }

    @Test
    void validateConnection_missingConfigReturnsFailure() {
        ValidateConnectionResponse response = connector.validateConnection(null);
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertFalse(response.isValid());
    }

    @Test
    void validateConnection_missingRequiredAttributeReturnsFailure() {
        ConnectionConfig config = ConnectionConfig.builder().withConnectionInfoId(100).build();
        ValidateConnectionResponse response = connector.validateConnection(config);
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertFalse(response.isValid());
        assertTrue(response.getMessage().toLowerCase().contains("required"));
    }

    @Test
    void exchangeAttributes_roundTrip_preservesConnectorSpecificValues() {
        Map<String, ConnectionAttribute> attrs = connector.getAttributes();
        attrs.get(ZohodeskConstants.KEY_BASE_URL).setValue("https://desk.zoho.eu/api/v1");
        attrs.get(ZohodeskConstants.KEY_ACCOUNTS_URL).setValue("https://accounts.zoho.eu");
        attrs.get(ZohodeskConstants.KEY_ORG_ID).setValue("2389290");
        attrs.get(ZohodeskConstants.KEY_CLIENT_ID).setValue("cid");
        attrs.get(ZohodeskConstants.KEY_CLIENT_SECRET).setValue("csecret");
        attrs.get(ZohodeskConstants.KEY_REFRESH_TOKEN).setValue("rtoken");

        ConnInfo connInfo = connector.exchangeAttributes(attrs);
        assertNotNull(connInfo);
        assertEquals(ZohodeskConstants.SERVER_TYPE, connInfo.getServertype());

        Map<String, ConnectionAttribute> roundTrip = connector.exchangeAttributes(connInfo);
        assertEquals("https://desk.zoho.eu/api/v1", roundTrip.get(ZohodeskConstants.KEY_BASE_URL).getValue());
        assertEquals("https://accounts.zoho.eu", roundTrip.get(ZohodeskConstants.KEY_ACCOUNTS_URL).getValue());
        assertEquals("2389290", roundTrip.get(ZohodeskConstants.KEY_ORG_ID).getValue());
        assertEquals("cid", roundTrip.get(ZohodeskConstants.KEY_CLIENT_ID).getValue());
        assertEquals("csecret", roundTrip.get(ZohodeskConstants.KEY_CLIENT_SECRET).getValue());
        assertEquals("rtoken", roundTrip.get(ZohodeskConstants.KEY_REFRESH_TOKEN).getValue());
    }
}
