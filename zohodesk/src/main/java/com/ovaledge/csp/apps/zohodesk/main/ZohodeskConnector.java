package com.ovaledge.csp.apps.zohodesk.main;

import com.ovaledge.csp.apps.zohodesk.client.ZohodeskClient;
import com.ovaledge.csp.apps.zohodesk.constants.ZohodeskConstants;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SdkConnector(artifactId = "zohodesk")
public class ZohodeskConnector extends BaseAppConnector implements AppsConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZohodeskConnector.class);

    private final ZohodeskMetadataService metadataService = new ZohodeskMetadataService();
    private final ZohodeskQueryService queryService = new ZohodeskQueryService();
    private final ZohodeskClient client = new ZohodeskClient();

    @Override
    public String getServerType() {
        return ZohodeskConstants.SERVER_TYPE;
    }

    @Override
    public ValidateConnectionResponse validateConnection(ConnectionConfig config) {
        try {
            String validationError = validateRequiredAttributes(config);
            if (validationError != null) {
                return new ValidateConnectionResponse().withSuccess(false).withValid(false).withMessage(validationError);
            }
            client.validateConnection(config);
            return new ValidateConnectionResponse().withSuccess(true).withValid(true).withMessage(ZohodeskConstants.MSG_SUCCESS);
        } catch (Exception ex) {
            LOGGER.warn("Zoho Desk connection validation failed: {}", ex.getMessage());
            return new ValidateConnectionResponse()
                    .withSuccess(false)
                    .withValid(false)
                    .withMessage(ex.getMessage() != null ? ex.getMessage() : "Connection validation failed.");
        }
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

        ConnectionAttribute baseUrl = new ConnectionAttribute(
                ZohodeskConstants.LABEL_BASE_URL,
                ZohodeskConstants.DEFAULT_BASE_URL,
                ZohodeskConstants.DESC_BASE_URL,
                15,
                ZohodeskConstants.KEY_BASE_URL);
        baseUrl.setRequired(true);

        ConnectionAttribute accountsUrl = new ConnectionAttribute(
                ZohodeskConstants.LABEL_ACCOUNTS_URL,
                ZohodeskConstants.DEFAULT_ACCOUNTS_URL,
                ZohodeskConstants.DESC_ACCOUNTS_URL,
                20,
                ZohodeskConstants.KEY_ACCOUNTS_URL);
        accountsUrl.setRequired(true);

        ConnectionAttribute orgId = new ConnectionAttribute(
                ZohodeskConstants.LABEL_ORG_ID,
                "",
                ZohodeskConstants.DESC_ORG_ID,
                25,
                ZohodeskConstants.KEY_ORG_ID);
        orgId.setRequired(true);

        ConnectionAttribute clientId = new ConnectionAttribute(
                ZohodeskConstants.LABEL_CLIENT_ID,
                "",
                ZohodeskConstants.DESC_CLIENT_ID,
                30,
                ZohodeskConstants.KEY_CLIENT_ID);
        clientId.setRequired(true);

        ConnectionAttribute clientSecret = new ConnectionAttribute(
                ZohodeskConstants.LABEL_CLIENT_SECRET,
                "",
                ZohodeskConstants.DESC_CLIENT_SECRET,
                35,
                ZohodeskConstants.KEY_CLIENT_SECRET);
        clientSecret.setRequired(true);
        clientSecret.setType(ConnectionAttribute.Type.PASSWORD);
        clientSecret.setMasked(true);
        clientSecret.setSecretManagerAttr(true);

        ConnectionAttribute refreshToken = new ConnectionAttribute(
                ZohodeskConstants.LABEL_REFRESH_TOKEN,
                "",
                ZohodeskConstants.DESC_REFRESH_TOKEN,
                40,
                ZohodeskConstants.KEY_REFRESH_TOKEN);
        refreshToken.setRequired(true);
        refreshToken.setType(ConnectionAttribute.Type.PASSWORD);
        refreshToken.setMasked(true);
        refreshToken.setSecretManagerAttr(true);

        ConnectionAttribute accessToken = new ConnectionAttribute(
                ZohodeskConstants.LABEL_ACCESS_TOKEN,
                "",
                ZohodeskConstants.DESC_ACCESS_TOKEN,
                45,
                ZohodeskConstants.KEY_ACCESS_TOKEN);
        accessToken.setType(ConnectionAttribute.Type.HIDDEN);
        accessToken.setMasked(true);
        accessToken.setSecretManagerAttr(true);

        ConnectionAttribute scope = new ConnectionAttribute(
                ZohodeskConstants.LABEL_SCOPE,
                ZohodeskConstants.DEFAULT_SCOPE,
                ZohodeskConstants.DESC_SCOPE,
                50,
                ZohodeskConstants.KEY_SCOPE);

        attributes.put(ZohodeskConstants.KEY_BASE_URL, baseUrl);
        attributes.put(ZohodeskConstants.KEY_ACCOUNTS_URL, accountsUrl);
        attributes.put(ZohodeskConstants.KEY_ORG_ID, orgId);
        attributes.put(ZohodeskConstants.KEY_CLIENT_ID, clientId);
        attributes.put(ZohodeskConstants.KEY_CLIENT_SECRET, clientSecret);
        attributes.put(ZohodeskConstants.KEY_REFRESH_TOKEN, refreshToken);
        attributes.put(ZohodeskConstants.KEY_ACCESS_TOKEN, accessToken);
        attributes.put(ZohodeskConstants.KEY_SCOPE, scope);

        getGovernanceAttributes(attributes);
        getSecurityAndGovernanceRolesAttributes(attributes);
        return attributes;
    }

    @Override
    public Map<String, ConnectionAttribute> exchangeAttributes(ConnInfo connInfo) {
        Map<String, ConnectionAttribute> exchangeAttributes = super.exchangeAttributes(connInfo, getAttributes());
        if (connInfo != null && connInfo.getAdditionalAttr() != null) {
            for (String key : new String[]{
                    ZohodeskConstants.KEY_BASE_URL,
                    ZohodeskConstants.KEY_ACCOUNTS_URL,
                    ZohodeskConstants.KEY_ORG_ID,
                    ZohodeskConstants.KEY_CLIENT_ID,
                    ZohodeskConstants.KEY_CLIENT_SECRET,
                    ZohodeskConstants.KEY_REFRESH_TOKEN,
                    ZohodeskConstants.KEY_ACCESS_TOKEN,
                    ZohodeskConstants.KEY_SCOPE
            }) {
                if (connInfo.getAdditionalAttr().containsKey(key) && exchangeAttributes.containsKey(key)) {
                    ConnectionAttribute saved = connInfo.getAdditionalAttr().get(key);
                    if (saved != null && saved.getValue() != null) {
                        exchangeAttributes.get(key).setValue(saved.getValue());
                    }
                }
            }
        }
        return exchangeAttributes;
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

    private String validateRequiredAttributes(ConnectionConfig config) {
        if (config == null) {
            return "Invalid connection config.";
        }
        if (isBlank(ZohodeskClient.get(config, ZohodeskConstants.KEY_BASE_URL))) {
            return ZohodeskConstants.MSG_MISSING_BASE_URL;
        }
        if (isBlank(ZohodeskClient.get(config, ZohodeskConstants.KEY_ACCOUNTS_URL))) {
            return ZohodeskConstants.MSG_MISSING_ACCOUNTS_URL;
        }
        if (isBlank(ZohodeskClient.get(config, ZohodeskConstants.KEY_ORG_ID))) {
            return ZohodeskConstants.MSG_MISSING_ORG_ID;
        }
        if (isBlank(ZohodeskClient.get(config, ZohodeskConstants.KEY_CLIENT_ID))) {
            return ZohodeskConstants.MSG_MISSING_CLIENT_ID;
        }
        if (isBlank(ZohodeskClient.get(config, ZohodeskConstants.KEY_CLIENT_SECRET))) {
            return ZohodeskConstants.MSG_MISSING_CLIENT_SECRET;
        }
        if (isBlank(ZohodeskClient.get(config, ZohodeskConstants.KEY_REFRESH_TOKEN))) {
            return ZohodeskConstants.MSG_MISSING_REFRESH_TOKEN;
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
