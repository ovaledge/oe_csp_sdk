package com.ovaledge.csp.apps.monetdb.main;

import com.ovaledge.csp.sdk.core.SdkCoreConstants;
import com.ovaledge.csp.apps.monetdb.constants.MonetDBConstants;
import com.ovaledge.csp.dto.model.ConnInfo;
import com.ovaledge.csp.dto.model.ConnectionAttribute;
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
import com.ovaledge.csp.v3.core.connectionpool.core.ConnectionPoolManager;
import com.ovaledge.csp.v3.core.connectionpool.core.ConnectionResource;
import com.ovaledge.csp.v3.core.connectionpool.enums.ResourceType;
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
 * MonetDB Apps connector: JDBC via ConnectionPoolManager.
 * Uses ConnectionConfig as-is (no buildConnectionConfig). No Client class.
 */
@SdkConnector(artifactId = "monetdb")
public class MonetDBConnector extends BaseAppConnector implements AppsConnector {

    private static final Logger LOG = LoggerFactory.getLogger(MonetDBConnector.class);

    private final MonetDBMetadataService metadataService = new MonetDBMetadataService();
    private final MonetDBQueryService queryService = new MonetDBQueryService();

/**
     * Return the server type identifier for this connector.
     *
     * This identifier is used by the platform to route requests to the correct connector implementation.
     *
     * @return lowercase server type string (e.g. "monetdb")
     */
    @Override
    public String getServerType() {
        return MonetDBConstants.SERVER_TYPE;
    }

    @Override
    public ValidateConnectionResponse validateConnection(ConnectionConfig config) throws Exception {
        if (config == null) {
            return new ValidateConnectionResponse().withSuccess(false).withMessage("Invalid connection config.");
        }
        config = ensureConnectionConfig(config);
        if (config.getJdbcUrl() == null || config.getJdbcUrl().isBlank()) {
            String host = getEffectiveHost(config);
            String database = getEffectiveDatabase(config);
            if (host == null || host.isBlank()) {
                return new ValidateConnectionResponse().withSuccess(false).withMessage(MonetDBConstants.MSG_MISSING_HOST);
            }
            if (database == null || database.isBlank()) {
                return new ValidateConnectionResponse().withSuccess(false).withMessage(MonetDBConstants.MSG_MISSING_DATABASE);
            }
        }
        try {
            ConnectionResource resource = (ConnectionResource) ConnectionPoolManager.getInstance()
                    .getOrCreateResource(config, ResourceType.JDBC);
            resource.queryForList("SELECT 1", "testConnection", false);
            return new ValidateConnectionResponse().withSuccess(true).withValid(true)
                    .withMessage(MonetDBConstants.MSG_SUCCESS);
        } catch (Exception e) {
            ConnectionPoolManager.getInstance().removeResource(config.getConnectionInfoId());
            return new ValidateConnectionResponse().withSuccess(false)
                    .withMessage("Connection failed: " + e.getMessage());
        }
    }

    private static String getEffectiveHost(ConnectionConfig config) {
        if (config.getHost() != null && !config.getHost().isBlank()) {
            return config.getHost();
        }
        return getFromAdditionalAttributes(config, MonetDBConstants.KEY_HOST);
    }

    private static String getEffectiveDatabase(ConnectionConfig config) {
        if (config.getDatabase() != null && !config.getDatabase().isBlank()) {
            return config.getDatabase();
        }
        return getFromAdditionalAttributes(config, MonetDBConstants.KEY_DATABASE);
    }

    private static int getEffectivePort(ConnectionConfig config) {
        if (config.getPort() > 0) {
            return config.getPort();
        }
        String p = getFromAdditionalAttributes(config, MonetDBConstants.KEY_PORT);
        if (p != null && !p.isBlank()) {
            try {
                return Integer.parseInt(p.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return MonetDBConstants.DEFAULT_PORT;
    }

    private static String getFromAdditionalAttributes(ConnectionConfig config, String key) {
        Map<String, String> attrs = config.getAdditionalAttributes();
        if (attrs == null) return null;
        String v = attrs.get(key);
        return (v != null && !v.isBlank()) ? v.trim() : null;
    }

    private static String buildJdbcUrl(String host, int port, String database) {
        return MonetDBConstants.JDBC_URL_PREFIX + host + ":" + port + "/" + database;
    }

    /**
     * Ensures ConnectionConfig has jdbcUrl and credentials set, resolving from
     * config fields or additionalAttributes (e.g. MONETDB_HOST, MONETDB_PORT, MONETDB_DATABASE).
     * Call this before any JDBC use when config may have been built from API request with only additionalAttributes.
     *
     * @param config the original connection configuration
     * @return a new ConnectionConfig with resolved JDBC URL and credentials
     */
    public static ConnectionConfig ensureConnectionConfig(ConnectionConfig config) {
        if (config == null) return null;
        ConnectionConfig.ConnectionConfigBuilder builder = config.toBuilder();
        boolean modified = false;
        String jdbcUrl = config.getJdbcUrl();
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            String host = getEffectiveHost(config);
            String database = getEffectiveDatabase(config);
            if (host != null && !host.isBlank() && database != null && !database.isBlank()) {
                int port = getEffectivePort(config);
                builder.withJdbcUrl(buildJdbcUrl(host, port, database));
                modified = true;
                if (config.getHost() == null || config.getHost().isBlank()) builder.withHost(host);
                if (config.getPort() <= 0) builder.withPort(port);
                if (config.getDatabase() == null || config.getDatabase().isBlank()) builder.withDatabase(database);
            }
        }
        if (config.getUsername() == null || config.getUsername().isBlank()) {
            String u = getFromAdditionalAttributes(config, MonetDBConstants.KEY_USERNAME);
            if (u != null) { builder.withUsername(u); modified = true; }
        }
        if (config.getPassword() == null || config.getPassword().isBlank()) {
            String p = getFromAdditionalAttributes(config, MonetDBConstants.KEY_PASSWORD);
            if (p != null) { builder.withPassword(p); modified = true; }
        }
        return modified ? builder.build() : config;
    }

    /**
     * Return the {@link MetadataService} implementation for this connector.
     *
     * @return metadata service instance
     */
    @Override
    public MetadataService getMetadataService() {
        return metadataService;
    }

    /**
     * Return the {@link QueryService} implementation for this connector.
     *
     * @return query service instance
     */
    @Override
    public QueryService getQueryService() {
        return queryService;
    }

    @Override
    public Map<String, ConnectionAttribute> getAttributes() {
        Map<String, ConnectionAttribute> attributes = new LinkedHashMap<>();
        getCredentialManagerCommonAttributes(attributes);
        attributes.putAll(getGenericAttributes());

        ConnectionAttribute host = new ConnectionAttribute(MonetDBConstants.LABEL_HOST, "",
                MonetDBConstants.DESC_HOST, 15, MonetDBConstants.KEY_HOST);
        host.setRequired(true);
        ConnectionAttribute port = new ConnectionAttribute(MonetDBConstants.LABEL_PORT, String.valueOf(MonetDBConstants.DEFAULT_PORT),
                MonetDBConstants.DESC_PORT, 20, MonetDBConstants.KEY_PORT);
        ConnectionAttribute database = new ConnectionAttribute(MonetDBConstants.LABEL_DATABASE, "",
                MonetDBConstants.DESC_DATABASE, 25, MonetDBConstants.KEY_DATABASE);
        database.setRequired(true);
        ConnectionAttribute username = new ConnectionAttribute(MonetDBConstants.LABEL_USERNAME, "",
                MonetDBConstants.DESC_USERNAME, 30, MonetDBConstants.KEY_USERNAME);
        ConnectionAttribute password = new ConnectionAttribute(MonetDBConstants.LABEL_PASSWORD, "",
                MonetDBConstants.DESC_PASSWORD, 35, MonetDBConstants.KEY_PASSWORD);
        password.setType(ConnectionAttribute.Type.PASSWORD);
        password.setMasked(true);
        password.setSecretManagerAttr(true);

        attributes.put(MonetDBConstants.KEY_HOST, host);
        attributes.put(MonetDBConstants.KEY_PORT, port);
        attributes.put(MonetDBConstants.KEY_DATABASE, database);
        attributes.put(MonetDBConstants.KEY_USERNAME, username);
        attributes.put(MonetDBConstants.KEY_PASSWORD, password);

        getGovernanceAttributes(attributes);
        getSecurityAndGovernanceRolesAttributes(attributes);
        return attributes;
    }

    @Override
    public Map<String, ConnectionAttribute> exchangeAttributes(ConnInfo connInfo) {
        Map<String, ConnectionAttribute> exchangeAttributes = super.exchangeAttributes(connInfo, getAttributes());
        if (connInfo != null && connInfo.getAdditionalAttr() != null) {
            Map<String, ConnectionAttribute> savedAttrs = connInfo.getAdditionalAttr();
            for (String key : new String[]{
                    MonetDBConstants.KEY_HOST, MonetDBConstants.KEY_PORT, MonetDBConstants.KEY_DATABASE,
                    MonetDBConstants.KEY_USERNAME, MonetDBConstants.KEY_PASSWORD
            }) {
                if (savedAttrs.containsKey(key) && exchangeAttributes.containsKey(key)) {
                    ConnectionAttribute saved = savedAttrs.get(key);
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
            LogUtils.logSystemWarn(LOG, "MonetDB processAppObjectsForEdgi failed.", e);
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
