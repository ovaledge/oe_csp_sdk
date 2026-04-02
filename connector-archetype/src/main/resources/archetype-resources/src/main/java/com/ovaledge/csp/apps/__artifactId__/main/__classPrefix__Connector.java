package com.ovaledge.csp.apps.${artifactId}.main;

import com.ovaledge.csp.apps.${artifactId}.constants.${classPrefix}Constants;
import com.ovaledge.csp.dto.model.ConnInfo;
import com.ovaledge.csp.dto.model.ConnectionAttribute;
import com.ovaledge.csp.sdk.core.SdkCoreConstants;
import com.ovaledge.csp.v3.core.apps.model.response.ValidateConnectionResponse;
import com.ovaledge.csp.v3.core.apps.service.AppsConnector;
import com.ovaledge.csp.v3.core.apps.service.BaseAppConnector;
import com.ovaledge.csp.v3.core.apps.service.SdkConnector;
import com.ovaledge.csp.v3.core.apps.service.MetadataService;
import com.ovaledge.csp.v3.core.apps.service.QueryService;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import com.ovaledge.csp.vo.SecretsManagerVo;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

@SdkConnector(artifactId = "${artifactId}")
public class ${classPrefix}Connector extends BaseAppConnector implements AppsConnector {

    private final ${classPrefix}MetadataService metadataService = new ${classPrefix}MetadataService();
    private final ${classPrefix}QueryService queryService = new ${classPrefix}QueryService();

    @Override
    public String getServerType() {
        return ${classPrefix}Constants.SERVER_TYPE;
    }

    @Override
    public ValidateConnectionResponse validateConnection(ConnectionConfig config) {
        return new ValidateConnectionResponse()
                .withSuccess(true)
                .withValid(true)
                .withMessage("Connection validation not implemented.");
    }

    @Override
    public MetadataService getMetadataService() {
        return metadataService;
    }

    @Override
    public QueryService getQueryService() {
        return queryService;
    }

    @Override
    public Map<String, ConnectionAttribute> getAttributes() {

        Map<String, ConnectionAttribute> attributes = new LinkedHashMap<>();
        getCredentialManagerCommonAttributes(attributes);
        attributes.putAll(getGenericAttributes());

        /*
         * Connector-specific attributes
         *
         * Add connection form fields required by your connector here. Use constants
         * defined in ${classPrefix}Constants for keys and values.
         *
         * Example:
         * attributes.put(${classPrefix}Constants.HOST, new ConnectionAttribute()
         *      .withLabel("Host")
         *      .withValue("")
         *      .withType(ConnectionAttribute.Type.STRING));
         *
         * - Keep attribute keys stable so the UI and saved connection configs remain compatible.
         * - Prefer existing helper methods (getCredentialManagerCommonAttributes, getGenericAttributes)
         *   for common fields and only add the connector-specific ones here.
         */

        getGovernanceAttributes(attributes);
        getSecurityAndGovernanceRolesAttributes(attributes);
        return attributes;
    }

    @Override
    public Map<String, ConnectionAttribute> exchangeAttributes(ConnInfo connInfo) {
        return super.exchangeAttributes(connInfo, getAttributes());
    }

    @Override
    public ConnInfo exchangeAttributes(Map<String, ConnectionAttribute> attributes) {
        ConnInfo connInfo = new ConnInfo(getServerType());
        if (attributes == null) {
            return connInfo;
        }
        for (Map.Entry<String, ConnectionAttribute> entry : attributes.entrySet()) {
            String key = entry.getKey();
            ConnectionAttribute value = entry.getValue();
            if (value == null || value.getValue() == null) {
                continue;
            }
            if (SdkCoreConstants.CONNECTION_ATTR_CONN_TYPE.equalsIgnoreCase(key)) {
                try {
                    connInfo.setConType(Integer.valueOf(value.getValue()));
                } catch (NumberFormatException ignored) {
                }
            } else if (SdkCoreConstants.CONNECTION_ATTR_NAME.equalsIgnoreCase(key)) {
                connInfo.setName(value.getValue());
            } else if (SdkCoreConstants.CONNECTION_ATTR_CONN_DESC.equalsIgnoreCase(key)) {
                connInfo.setDescription(value.getValue());
            } else if (SdkCoreConstants.CONN_ATTR_BRIDGEID.equalsIgnoreCase(key)) {
                try {
                    connInfo.setBridgeId(Integer.valueOf(value.getValue()));
                } catch (NumberFormatException ignored) {
                }
            } else {
                connInfo.getAdditionalAttr().put(key, value);
            }
        }
        return connInfo;
    }

    @Override
    public Map<String, ConnectionAttribute> getExtendedAttributes(Map<String, ConnectionAttribute> attributes) {
        return getAttributes();
    }

    @Override
    public ConnInfo getActualValueFromSecretsManagerForBridge(JSONObject object, ConnInfo connInfo) {
        return super.getSecretValueFromSecretsManagerForBridge(object, connInfo, exchangeAttributes(connInfo));
    }

    @Override
    public ConnInfo exchangeVaultAttributes(ConnInfo connInfo, JSONObject data, String path) {
        return super.exchangeVaultAttributes(connInfo, data, path, exchangeAttributes(connInfo));
    }

    @Override
    public SecretsManagerVo createUpdateSecretsManagerObjectAndConnInfo(String secretName, ConnInfo connInfo) {
        return super.getSecretsManagerObjectAndConnInfo(secretName, connInfo);
    }

    @Override
    public String getVaultPath(ConnInfo connInfo) {
        return super.getVaultPath(connInfo, exchangeAttributes(connInfo));
    }
}
