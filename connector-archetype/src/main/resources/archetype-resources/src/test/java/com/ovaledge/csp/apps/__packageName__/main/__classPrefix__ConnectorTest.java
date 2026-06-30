package com.ovaledge.csp.apps.${packageName}.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ovaledge.csp.apps.${packageName}.constants.${classPrefix}Constants;
import com.ovaledge.csp.dto.model.ConnectionAttribute;
import com.ovaledge.csp.v3.core.apps.model.response.ValidateConnectionResponse;
import com.ovaledge.csp.v3.core.apps.service.MetadataService;
import com.ovaledge.csp.v3.core.apps.service.QueryService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AUTO-GENERATED TEST TEMPLATE.
 *
 * <p>TODO: Replace skeleton assertions with connector-specific behavior tests.
 */
@ExtendWith(MockitoExtension.class)
class ${classPrefix}ConnectorTest {

    private final ${classPrefix}Connector connector = new ${classPrefix}Connector();

    @Test
    void getServerType_returnsConnectorServerType() {
        assertEquals(${classPrefix}Constants.SERVER_TYPE, connector.getServerType());
    }

    @Test
    void validateConnection_returnsDefaultValidationResponse() {
        ValidateConnectionResponse response = connector.validateConnection(null);
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.getValid());
    }

    @Test
    void getMetadataService_returnsMetadataServiceInstance() {
        MetadataService metadataService = connector.getMetadataService();
        assertNotNull(metadataService);
    }

    @Test
    void getQueryService_returnsQueryServiceInstance() {
        QueryService queryService = connector.getQueryService();
        assertNotNull(queryService);
    }

    @Test
    void getAttributes_returnsNonEmptyAttributes() {
        Map<String, ConnectionAttribute> attributes = connector.getAttributes();
        assertNotNull(attributes);
        assertTrue(!attributes.isEmpty());
    }

    // TODO: add tests for exchangeAttributes_returnsMappedConnInfo.
    // TODO: add tests for exchangeAttributes_returnsMappedAttributes.
    // TODO: add tests for getExtendedAttributes_returnsConnectorAttributes.
    // TODO: add tests for getActualValueFromSecretsManagerForBridge_returnsResolvedConnInfo.
    // TODO: add tests for exchangeVaultAttributes_returnsUpdatedConnInfo.
    // TODO: add tests for createUpdateSecretsManagerObjectAndConnInfo_returnsSecretsManagerPayload.
    // TODO: add tests for getVaultPath_returnsResolvedVaultPath.
    // TODO: for each future void method, create a TODO-only skeleton test:
    //       void <methodName>_<expectedBehavior>() { /* TODO implement */ }
}
