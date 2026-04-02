package com.ovaledge.csp.apps.awsconsole.main;

import com.ovaledge.csp.apps.awsconsole.client.AwsConsoleClient;
import com.ovaledge.csp.apps.awsconsole.constants.AwsConsoleConstants;
import com.ovaledge.csp.dto.model.ConnInfo;
import com.ovaledge.csp.dto.model.ConnectionAttribute;
import com.ovaledge.csp.sdk.core.SdkCoreConstants;
import com.ovaledge.csp.sdk.edgi.EdgiBridgeUtils;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import com.ovaledge.csp.v3.core.apps.model.request.EdgiConnectorObjectRequest;
import com.ovaledge.csp.v3.core.apps.model.request.QueryRequest;
import com.ovaledge.csp.v3.core.apps.model.response.EdgiConnectorObjectResponse;
import com.ovaledge.csp.v3.core.apps.model.response.QueryResponse;
import com.ovaledge.csp.v3.core.apps.model.response.ValidateConnectionResponse;
import com.ovaledge.csp.v3.core.apps.service.AppsConnector;
import com.ovaledge.csp.v3.core.apps.service.BaseAppConnector;
import com.ovaledge.csp.v3.core.apps.service.SdkConnector;
import com.ovaledge.csp.v3.core.apps.service.MetadataService;
import com.ovaledge.csp.v3.core.apps.service.QueryService;
import com.ovaledge.csp.v3.core.apps.utils.LogUtils;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import com.ovaledge.csp.vo.SecretsManagerVo;
import com.ovaledge.oasis.config.GovernanceRoleConfig;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AWS Console Apps connector: via AWS SDK; uses AwsConsoleClient for all source calls.
 * On auth failure the client calls ConnectionPoolManager.removeResource.
 */
@SdkConnector(artifactId = "awsconsole")
public class AwsConsoleConnector extends BaseAppConnector implements AppsConnector {

    private static final Logger LOG = LoggerFactory.getLogger(AwsConsoleConnector.class);

    private final AwsConsoleMetadataService metadataService = new AwsConsoleMetadataService();
    private final AwsConsoleQueryService queryService = new AwsConsoleQueryService();

    @Override
    public String getServerType() {
        return AwsConsoleConstants.SERVER_TYPE;
    }

    @Override
    public ValidateConnectionResponse validateConnection(ConnectionConfig config) throws Exception {
        if (config == null) {
            return new ValidateConnectionResponse().withSuccess(false).withMessage("Invalid connection config.");
        }
        config = ensureConnectionConfig(config);
        String accessKey = AwsConsoleClient.getAccessKeyId(config);
        String secretKey = AwsConsoleClient.getSecretAccessKey(config);
        if (accessKey == null || accessKey.isBlank()) {
            return new ValidateConnectionResponse().withSuccess(false).withMessage(AwsConsoleConstants.MSG_MISSING_ACCESS_KEY);
        }
        if (secretKey == null || secretKey.isBlank()) {
            return new ValidateConnectionResponse().withSuccess(false).withMessage(AwsConsoleConstants.MSG_MISSING_SECRET_KEY);
        }
        try {
            boolean valid = new AwsConsoleClient().validateConnection(config);
            if (valid) {
                return new ValidateConnectionResponse().withSuccess(true).withValid(true).withMessage(AwsConsoleConstants.MSG_SUCCESS);
            }
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : "AWS credentials validation failed.";
            return new ValidateConnectionResponse().withSuccess(false).withMessage(message);
        }
        return new ValidateConnectionResponse().withSuccess(false).withMessage("AWS credentials validation failed.");
    }

    /**
     * Ensures ConnectionConfig has credentials set from config fields or additionalAttributes.
     *
     * This helper resolves access key and secret key from either the ConnectionConfig username/password
     * fields or from additionalAttributes (keys defined in {@link AwsConsoleConstants}).
     * It should be called before any AWS SDK operation that requires credentials.
     *
     * @param config connection configuration to normalize
     * @return a new ConnectionConfig with resolved credentials
     */
    public static ConnectionConfig ensureConnectionConfig(ConnectionConfig config) {
        if (config == null) return null;
        ConnectionConfig.ConnectionConfigBuilder builder = config.toBuilder();
        boolean modified = false;
        if (config.getUsername() == null || config.getUsername().isBlank()) {
            String v = getFromAdditionalAttributes(config, AwsConsoleConstants.KEY_ACCESS_KEY_ID);
            if (v != null) { builder.withUsername(v); modified = true; }
        }
        if (config.getPassword() == null || config.getPassword().isBlank()) {
            String v = getFromAdditionalAttributes(config, AwsConsoleConstants.KEY_SECRET_ACCESS_KEY);
            if (v != null) { builder.withPassword(v); modified = true; }
        }
        return modified ? builder.build() : config;
    }

    private static String getFromAdditionalAttributes(ConnectionConfig config, String key) {
        Map<String, String> attrs = config.getAdditionalAttributes();
        if (attrs == null) return null;
        String v = attrs.get(key);
        return (v != null && !v.isBlank()) ? v.trim() : null;
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

        ConnectionAttribute accessKey = new ConnectionAttribute(AwsConsoleConstants.LABEL_ACCESS_KEY_ID, "",
                AwsConsoleConstants.DESC_ACCESS_KEY_ID, 15, AwsConsoleConstants.KEY_ACCESS_KEY_ID);
        accessKey.setRequired(true);
        ConnectionAttribute secretKey = new ConnectionAttribute(AwsConsoleConstants.LABEL_SECRET_ACCESS_KEY, "",
                AwsConsoleConstants.DESC_SECRET_ACCESS_KEY, 20, AwsConsoleConstants.KEY_SECRET_ACCESS_KEY);
        secretKey.setType(ConnectionAttribute.Type.PASSWORD);
        secretKey.setMasked(true);
        secretKey.setSecretManagerAttr(true);
        secretKey.setRequired(true);
        ConnectionAttribute region = new ConnectionAttribute(AwsConsoleConstants.LABEL_REGION, AwsConsoleConstants.DEFAULT_REGION,
                AwsConsoleConstants.DESC_REGION, 25, AwsConsoleConstants.KEY_REGION);

        attributes.put(AwsConsoleConstants.KEY_ACCESS_KEY_ID, accessKey);
        attributes.put(AwsConsoleConstants.KEY_SECRET_ACCESS_KEY, secretKey);
        attributes.put(AwsConsoleConstants.KEY_REGION, region);

        getGovernanceAttributes(attributes);
        getSecurityAndGovernanceRolesAttributes(attributes);
        return attributes;
    }

    @Override
    public Map<String, ConnectionAttribute> exchangeAttributes(ConnInfo connInfo) {
        Map<String, ConnectionAttribute> exchangeAttributes = super.exchangeAttributes(connInfo, getAttributes());
        if (connInfo != null && connInfo.getAdditionalAttr() != null) {
            for (String key : new String[]{
                    AwsConsoleConstants.KEY_ACCESS_KEY_ID, AwsConsoleConstants.KEY_SECRET_ACCESS_KEY, AwsConsoleConstants.KEY_REGION
            }) {
                if (connInfo.getAdditionalAttr().containsKey(key) && exchangeAttributes.containsKey(key)) {
                    ConnectionAttribute saved = connInfo.getAdditionalAttr().get(key);
                    if (saved != null && saved.getValue() != null) {
                        exchangeAttributes.get(key).setValue(saved.getValue());
                    }
                }
            }
        }
        if (connInfo != null && connInfo.getAdditionalAttr() != null
                && connInfo.getAdditionalAttr().containsKey(SdkCoreConstants.CONNECTION_ATTR_CREDENTIAL_MANAGER)) {
            ConnectionAttribute cmAttr = connInfo.getAdditionalAttr().get(SdkCoreConstants.CONNECTION_ATTR_CREDENTIAL_MANAGER);
            String cmType = cmAttr != null ? cmAttr.getValue() : null;
            if (cmType != null && !StringUtils.equalsIgnoreCase(cmType, SdkCoreConstants.CONNECTION_ATTR_CM_DB)) {
                super.exchangeCredentialManagerAttributes(cmType, exchangeAttributes);
            }
        }
        return exchangeAttributes;
    }

    @Override
    public Map<String, ConnectionAttribute> getExtendedAttributes(Map<String, ConnectionAttribute> attributes) {
        Map<String, ConnectionAttribute> returnMap = getAttributes();
        String cmType = returnMap.containsKey(SdkCoreConstants.CONNECTION_ATTR_CREDENTIAL_MANAGER)
                ? returnMap.get(SdkCoreConstants.CONNECTION_ATTR_CREDENTIAL_MANAGER).getValue() : null;
        if (attributes != null && attributes.containsKey(SdkCoreConstants.CONNECTION_ATTR_CREDENTIAL_MANAGER)
                && attributes.get(SdkCoreConstants.CONNECTION_ATTR_CREDENTIAL_MANAGER) != null) {
            cmType = attributes.get(SdkCoreConstants.CONNECTION_ATTR_CREDENTIAL_MANAGER).getValue();
        }
        if (cmType != null && !StringUtils.equalsIgnoreCase(cmType, SdkCoreConstants.CONNECTION_ATTR_CM_DB)) {
            super.getAndRearrangeCredentialManager(cmType, returnMap);
        }
        return returnMap;
    }

    @Override
    public ConnInfo exchangeAttributes(Map<String, ConnectionAttribute> attributes) {
        ConnInfo connInfo = new ConnInfo(getServerType());
        Map<String, Map<String, String>> enabledGovRoles = GovernanceRoleConfig.getEnabledGovRoles();
        for (Map.Entry<String, ConnectionAttribute> entry : attributes.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(SdkCoreConstants.CONNECTION_ATTR_CONN_TYPE)) {
                try {
                    connInfo.setConType(Integer.valueOf(entry.getValue().getValue()));
                } catch (NumberFormatException e) {
                    connInfo.setConType(20); // Default connection type for app connectors
                }
            } else if (entry.getKey().equalsIgnoreCase(SdkCoreConstants.CONNECTION_ATTR_NAME)) {
                connInfo.setName(entry.getValue().getValue());
            } else if (entry.getKey().equalsIgnoreCase(SdkCoreConstants.CONNECTION_ATTR_CONN_DESC)) {
                connInfo.setDescription(entry.getValue().getValue());
            } else if (Objects.nonNull(enabledGovRoles.get(SdkCoreConstants.CONNECTION_ATTR_OWNER))
                    && entry.getKey().equalsIgnoreCase(SdkCoreConstants.CONNECTION_ATTR_OWNER)) {
                connInfo.setOwner(entry.getValue().getValue());
            } else if (Objects.nonNull(enabledGovRoles.get(SdkCoreConstants.CONNECTION_ATTR_STEWARD))
                    && entry.getKey().equalsIgnoreCase(SdkCoreConstants.CONNECTION_ATTR_STEWARD)) {
                connInfo.setSteward(entry.getValue().getValue());
            } else if (Objects.nonNull(enabledGovRoles.get(SdkCoreConstants.CONNECTION_ATTR_CUSTODIAN))
                    && entry.getKey().equalsIgnoreCase(SdkCoreConstants.CONNECTION_ATTR_CUSTODIAN)) {
                connInfo.setCustodian(entry.getValue().getValue());
            } else if (Objects.nonNull(enabledGovRoles.get(SdkCoreConstants.CONNECTION_ATTR_GOVERNANCEROLE4))
                    && entry.getKey().equalsIgnoreCase(SdkCoreConstants.CONNECTION_ATTR_GOVERNANCEROLE4)) {
                connInfo.setGoverananceRole4(entry.getValue().getValue());
            } else if (Objects.nonNull(enabledGovRoles.get(SdkCoreConstants.CONNECTION_ATTR_GOVERNANCEROLE5))
                    && entry.getKey().equalsIgnoreCase(SdkCoreConstants.CONNECTION_ATTR_GOVERNANCEROLE5)) {
                connInfo.setGoverananceRole5(entry.getValue().getValue());
            } else if (Objects.nonNull(enabledGovRoles.get(SdkCoreConstants.CONNECTION_ATTR_GOVERNANCEROLE6))
                    && entry.getKey().equalsIgnoreCase(SdkCoreConstants.CONNECTION_ATTR_GOVERNANCEROLE6)) {
                connInfo.setGoverananceRole6(entry.getValue().getValue());
            } else if (entry.getKey().equalsIgnoreCase(SdkCoreConstants.CONNECTION_ATTR_ENVIRONMENT_TYPE)) {
                connInfo.setEnvironment(entry.getValue().getValue());
            } else if (entry.getKey().equalsIgnoreCase(SdkCoreConstants.CONNECTOR_ADMIN_ROLES)) {
                connInfo.setConnectorAdminRoles(entry.getValue().getValue());
            } else if (entry.getKey().equalsIgnoreCase(SdkCoreConstants.CONN_ATTR_BRIDGEID)) {
                try {
                    connInfo.setBridgeId(Integer.valueOf(entry.getValue().getValue()));
                } catch (NumberFormatException e) {
                    connInfo.setBridgeId(0);
                }
            } else {
                connInfo.getAdditionalAttr().put(entry.getKey(), entry.getValue());
            }
        }
        return connInfo;
    }

    @Override
    public EdgiConnectorObjectResponse processAppObjectsForEdgi(EdgiConnectorObjectRequest request) {
        EdgiConnectorObjectResponse response = new EdgiConnectorObjectResponse();
        String entityName = request.getFullyQualifiedObjectName().split("\\.")[1];
        int workspaceId = request.getWorkspaceId();
        ConnInfo connInfo = request.getConnInfo();

        try {
            QueryRequest queryRequest = new QueryRequest();
            queryRequest.setEntityId(entityName);
            queryRequest.setEntityType(ObjectKind.ENTITY.value());
            queryRequest.setConnectionConfig(connInfo.toConnectionConfig());
            if (request.getObjectFieldNames() != null && !request.getObjectFieldNames().isEmpty()) {
                queryRequest.setFields(request.getObjectFieldNames());
            }
            QueryResponse queryResponse = queryService.fetchData(queryRequest);

            List<Map<String, Object>> data = queryResponse.getData();
            if (data == null) {
                data = new ArrayList<>();
            }

            String tempPath = EdgiBridgeUtils.getJpTempPath(connInfo);
            if (tempPath != null && !tempPath.trim().isEmpty()) {
                tempPath = (tempPath.endsWith(File.separator) ? tempPath : tempPath + File.separator);
            }

            Long rowCount = queryResponse.getTotalRows() != null ? Long.valueOf(queryResponse.getTotalRows()) : Long.valueOf(data.size());
            String countPath = tempPath + workspaceId + File.separator + "audit" + File.separator;
            EdgiBridgeUtils.writeTableRowCountToParquet(countPath, entityName, rowCount);

            if (rowCount == 0L || data.isEmpty()) {
                return EdgiBridgeUtils.handleEmptyEntity(entityName, workspaceId, tempPath, request);
            }

            boolean isProcessed = EdgiBridgeUtils.handleAppObjectsDataToParquet(data, tempPath, workspaceId, entityName);
            response.setObjectProcessed(isProcessed);

        } catch (Exception e) {
            LogUtils.logSystemWarn(LOG, "AWS Console processAppObjectsForEdgi failed.", e);
            response.setObjectProcessed(false);
            response.setEdgiProcessingMessage(e.getMessage());
        }
        return response;
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
