package com.ovaledge.csp.apps.app.controller;

import com.ovaledge.csp.apps.app.service.AppsService;
import com.ovaledge.csp.apps.app.service.AppsRegistry;
import com.ovaledge.csp.dto.model.ConnInfo;
import com.ovaledge.csp.v3.core.apps.model.request.ContainersRequest;
import com.ovaledge.csp.v3.core.apps.model.request.EdgiConnectorObjectRequest;
import com.ovaledge.csp.v3.core.apps.model.request.FieldsRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ObjectRequest;
import com.ovaledge.csp.v3.core.apps.model.request.QueryRequest;
import com.ovaledge.csp.v3.core.apps.model.response.*;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import com.ovaledge.csp.v3.core.apps.model.response.ValidateConnectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * REST Controller for Apps Connector Operations V1 API.
 * <p>
 * Provides comprehensive connector management capabilities including connection validation,
 * metadata extraction, and query execution. All endpoints use asynchronous processing with Callable for
 * improved performance and scalability.
 * </p>
 * <p>
 * <b>Key Features:</b>
 * <ul>
 *   <li><b>Async Processing:</b> All endpoints return Callable for non-blocking execution</li>
 *   <li><b>Connection Management:</b> Validation and configuration handling</li>
 *   <li><b>Metadata Services:</b> Supported objects, containers, objects, and fields metadata extraction</li>
 *   <li><b>Query Execution:</b> Data fetching from connectors</li>
 * </ul>
 * </p>
 * <p>
 * <b>Usage:</b>
 * <ul>
 *   <li>Use connection endpoints for setup and validation</li>
 *   <li>Use metadata endpoints for connector exploration</li>
 *   <li>Use query endpoints for data retrieval</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/v1")
@CrossOrigin(origins = "${csp.cors.allowed-origins:http://localhost:3000}")
public class AppsConnectorController {
    
    private static final Logger logger = LoggerFactory.getLogger(AppsConnectorController.class);
    
    @Autowired
    private AppsService appsService;
    
    @Autowired
    private AppsRegistry appsRegistry;
    
    // Connection validation endpoints
    
    /**
     * Validates a connector connection using the provided configuration.
     * <p>
     * Performs comprehensive connection validation including authentication,
     * network connectivity, and connector accessibility checks.
     * </p>
     *
     * @param config the connection configuration
     * @return Callable containing the validation response with success/failure status
     */
    @PostMapping("/connection/validate")
    public Callable<ResponseEntity<ValidateConnectionResponse>> validateConnection(@RequestBody ConnectionConfig config) {
        return () -> {
            if (config == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ValidateConnectionResponse().withValid(false).withMessage("Request body is required."));
            }
            logger.info("Validating connection for server type: {}", config.getServerType());
            try {
                ValidateConnectionResponse response = appsService.validateConnection(config);
                if (response.isValid()) {
                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
            } catch (Exception e) {
                logger.error("Error validating connection", e);
                ValidateConnectionResponse errorResponse = new ValidateConnectionResponse()
                        .withValid(false)
                        .withMessage("Validation failed: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        };
    }
    
    // Metadata endpoints
    
    /**
     * Retrieves supported objects (entities, reports, etc.).
     * <p>
     * Returns comprehensive information about available objects that can be queried
     * from the connector.
     * </p>
     *
     * @param config the connection configuration
     * @return Callable containing supported objects response
     */
    @PostMapping("/metadata/supported-objects")
    public Callable<ResponseEntity<SupportedObjectsResponse>> getSupportedObjects(@RequestBody ConnectionConfig config) {
        return () -> {
            if (config == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new SupportedObjectsResponse().withSuccess(false));
            }
            logger.info("Getting supported objects for server type: {}", config.getServerType());
            try {
                SupportedObjectsResponse response = appsService.getSupportedObjects(config);
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                logger.error("Error getting supported objects", e);
                SupportedObjectsResponse errorResponse = new SupportedObjectsResponse()
                        .withSuccess(false);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        };
    }
    
    /**
     * Retrieves containers (companies, organizations, etc.).
     * <p>
     * Returns comprehensive information about available containers within the connector,
     * which typically represent top-level organizational units.
     * </p>
     *
     * @param config the connection configuration
     * @return Callable containing containers response
     */
    @PostMapping("/metadata/containers")
    public Callable<ResponseEntity<ContainersResponse>> getContainers(@RequestBody ConnectionConfig config) {
        return () -> {
            if (config == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ContainersResponse().withSuccess(false));
            }
            logger.info("Getting containers for server type: {}", config.getServerType());
            try {
                ContainersRequest request = new ContainersRequest(config);
                ContainersResponse response = appsService.getContainers(request);
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                logger.error("Error getting containers", e);
                ContainersResponse errorResponse = new ContainersResponse()
                        .withSuccess(false);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        };
    }
    
    /**
     * Retrieves objects (entities or reports) under a container.
     * <p>
     * Returns comprehensive information about objects within the specified container,
     * including entity and report listings.
     * </p>
     *
     * @param config the connection configuration
     * @param entityType the entity type (e.g., "entity", "report")
     * @param containerId the container identifier
     * @return Callable containing object response
     */
    @PostMapping("/metadata/objects")
    public Callable<ResponseEntity<ObjectResponse>> getObjects(
            @RequestBody ConnectionConfig config,
            @RequestParam("entityType") String entityType,
            @RequestParam("containerId") String containerId,
            @RequestParam(value = "displayName", required = false) String displayName) {
        return () -> {
            if (config == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ObjectResponse().withSuccess(false));
            }
            logger.info("Getting objects for server type: {}, entityType: {}, containerId: {}, displayName: {}",
                config.getServerType(), entityType, containerId, displayName);
            try {
                ObjectRequest request = new ObjectRequest(config, entityType, containerId);
                if (displayName != null && !displayName.isBlank()) {
                    request.withFilter("displayName", displayName);
                }
                ObjectResponse response = appsService.getObjects(request);
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                logger.error("Error getting objects", e);
                ObjectResponse errorResponse = new ObjectResponse()
                        .withSuccess(false);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        };
    }
    
    /**
     * Retrieves fields for an entity or report.
     * <p>
     * Returns detailed information about fields within the specified entity or report,
     * including field types, properties, and metadata.
     * </p>
     *
     * @param config the connection configuration
     * @param entityType the entity type (e.g., "entity", "report")
     * @param containerId the container identifier
     * @param entityId the entity identifier
     * @return Callable containing fields response
     */
    @PostMapping("/metadata/fields")
    public Callable<ResponseEntity<FieldsResponse>> getFields(
            @RequestBody ConnectionConfig config,
            @RequestParam("entityType") String entityType,
            @RequestParam("containerId") String containerId,
            @RequestParam("entityId") String entityId,
            @RequestParam(value = "displayName", required = false) String displayName) {
        return () -> {
            if (config == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new FieldsResponse().withSuccess(false));
            }
            logger.info("Getting fields for server type: {}, entityType: {}, containerId: {}, entityId: {}", 
                config.getServerType(), entityType, containerId, entityId);
            try {
                FieldsRequest request = new FieldsRequest(config, entityType, containerId, entityId);
                if (displayName != null && !displayName.isBlank()) {
                    request.withFilter("displayName", displayName);
                }
                FieldsResponse response = appsService.getFields(request);
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                logger.error("Error getting fields", e);
                FieldsResponse errorResponse = new FieldsResponse()
                        .withSuccess(false);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        };
    }
    
    // Query endpoints
    
    /**
     * Executes a query to fetch entity or report data.
     * <p>
     * Supports fetching data from connectors with proper result set handling and error management.
     * This method provides a flexible interface for executing queries with parameter support.
     * </p>
     *
     * @param request the query request containing connection configuration, entity type, container, and query parameters
     * @return Callable containing query execution results
     */
    @PostMapping("/query")
    public Callable<ResponseEntity<QueryResponse>> executeQuery(
            @RequestBody QueryRequest request,
            @RequestParam(value = "displayName", required = false) String displayName) {
        return () -> {
            if (request == null || request.getConnectionConfig() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new QueryResponse().withSuccess(false).withMessage("Request body is required."));
            }
            if (displayName != null && !displayName.isBlank()) {
                request.withFilter("displayName", displayName);
            }
            logger.info("Executing query for server type: {}", request.getConnectionConfig().getServerType());
            try {
                QueryResponse response = appsService.executeQuery(request);
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                logger.error("Error executing query", e);
                QueryResponse errorResponse = new QueryResponse()
                        .withSuccess(false)
                        .withMessage("Query execution failed: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        };
    }

    @PostMapping("/edgi/ask")
    public Callable<ResponseEntity<EdgiConnectorObjectResponse>> askEdgi(@RequestBody AskEdgiRequest request) {
        return () -> {
            if (request == null || request.getConnectionConfig() == null || request.getFullyQualifiedObjectName() == null
                    || request.getFullyQualifiedObjectName().isBlank()) {
                EdgiConnectorObjectResponse errorResponse = new EdgiConnectorObjectResponse();
                errorResponse.setObjectProcessed(false);
                errorResponse.setEdgiProcessingMessage("Request body with fullyQualifiedObjectName and valid connectionConfig is required.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }
            EdgiConnectorObjectRequest edgiRequest = new EdgiConnectorObjectRequest();
            edgiRequest.setFullyQualifiedObjectName(request.getFullyQualifiedObjectName());
            edgiRequest.setWorkspaceId(request.getWorkspaceId());
            edgiRequest.setObjectFieldNames(request.getObjectFieldNames());
            edgiRequest.setConnInfo(ConnInfo.fromConnectionConfig(request.getConnectionConfig()));

            logger.info("Processing askEdgi for server type: {}",
                request.getConnectionConfig().getServerType());
            try {
                EdgiConnectorObjectResponse response = appsService.askEdgi(edgiRequest);
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                logger.error("Error processing askEdgi request", e);
                EdgiConnectorObjectResponse errorResponse = new EdgiConnectorObjectResponse();
                errorResponse.setObjectProcessed(false);
                errorResponse.setEdgiProcessingMessage("askEdgi execution failed: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        };
    }

    public static class AskEdgiRequest {
        private String fullyQualifiedObjectName;
        private Integer workspaceId;
        private List<String> objectFieldNames;
        private ConnectionConfig connectionConfig;

        public String getFullyQualifiedObjectName() {
            return fullyQualifiedObjectName;
        }

        public void setFullyQualifiedObjectName(String fullyQualifiedObjectName) {
            this.fullyQualifiedObjectName = fullyQualifiedObjectName;
        }

        public Integer getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(Integer workspaceId) {
            this.workspaceId = workspaceId;
        }

        public List<String> getObjectFieldNames() {
            return objectFieldNames;
        }

        public void setObjectFieldNames(List<String> objectFieldNames) {
            this.objectFieldNames = objectFieldNames;
        }

        public ConnectionConfig getConnectionConfig() {
            return connectionConfig;
        }

        public void setConnectionConfig(ConnectionConfig connectionConfig) {
            this.connectionConfig = connectionConfig;
        }
    }
    
    // Info endpoint
    
    /**
     * Retrieves information about available connectors.
     *
     * @return Callable containing connector information
     */
    @GetMapping("/info")
    public Callable<ResponseEntity<Map<String, Object>>> getConnectorInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("availableConnectors", appsRegistry.getAvailableServerTypes());
        info.put("connectorCount", appsRegistry.getAllConnectors().size());
        return () -> ResponseEntity.ok(info);
    }
    
    /**
     * Retrieves connection attributes for a specific connector.
     *
     * @param serverType the server type identifier
     * @return Callable containing connection attributes
     */
    @GetMapping("/connector/{serverType}/attributes")
    public Callable<ResponseEntity<Map<String, Object>>> getConnectorAttributes(
            @PathVariable("serverType") String serverType) {
        logger.info("Getting attributes for server type: {}", serverType);
        try {
            var connector = appsRegistry.getConnector(serverType);
            if (connector == null) {
                return () -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Connector not found: " + serverType));
            }
            
            var attributes = connector.getAttributes();
            // Convert ConnectionAttribute objects to serializable maps
            Map<String, Map<String, Object>> serializableAttributes = new HashMap<>();
            if (attributes != null) {
                attributes.forEach((attrKey, attr) -> {
                    Map<String, Object> attrMap = new HashMap<>();
                    
                    // ConnectionAttribute has getLabel() for name and getHelp() for description
                    attrMap.put("name", attr.getLabel() != null ? attr.getLabel() : attrKey);
                    attrMap.put("description", attr.getHelp() != null ? attr.getHelp() : "");
                    attrMap.put("type", attr.getType() != null ? attr.getType().toString() : null);
                    attrMap.put("required", attr.isRequired());
                    attrMap.put("masked", attr.isMasked());
                    attrMap.put("secretManagerAttr", attr.isSecretManagerAttr());
                    attrMap.put("sequenceDisplay", attr.getSequenceDisplay());
                    
                    if (attr.getDropdownList() != null && !attr.getDropdownList().isEmpty()) {
                        List<Map<String, Object>> dropdownItems = new ArrayList<>();
                        attr.getDropdownList().forEach(item -> {
                            Map<String, Object> itemMap = new HashMap<>();
                            // SelectItem has getKey() and getValue() - value is used as the label
                            String itemKey = item.getKey() != null ? item.getKey() : "";
                            String value = item.getValue() != null ? item.getValue() : itemKey;
                            
                            itemMap.put("value", value);
                            itemMap.put("label", value); // SelectItem value serves as the label
                            itemMap.put("key", itemKey);
                            dropdownItems.add(itemMap);
                        });
                        attrMap.put("dropdownList", dropdownItems);
                    }
                    
                    serializableAttributes.put(attrKey, attrMap);
                });
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("serverType", serverType);
            result.put("attributes", serializableAttributes);
            
            return () -> ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting connector attributes", e);
            return () -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get attributes: " + e.getMessage()));
        }
    }

    /**
     * Retrieves connector icon image.
     * <p>
     * Resolution order (first match wins):
     * <ol>
     *   <li>{@code classpath:/icons/db/{serverType}.jpeg}</li>
     *   <li>{@code classpath:/icons/db/{serverType}.jpg}</li>
     *   <li>{@code classpath:/icons/db/{serverType}.png}</li>
     *   <li>{@code classpath:/icons/db/{serverType}.svg}</li>
     *   <li>{@code classpath:/icons/{serverType}.jpeg}</li>
     *   <li>{@code classpath:/icons/{serverType}.jpg}</li>
     *   <li>{@code classpath:/icons/{serverType}.png}</li>
     *   <li>{@code classpath:/icons/{serverType}.svg}</li>
     *   <li>fallback {@code classpath:/templates/connector-generator/icon.png}</li>
     * </ol>
     * </p>
     *
     * @param serverType the connector server type identifier
     * @return icon bytes for connector icon
     */
    @GetMapping(value = "/connector/{serverType}/icon", produces = {
        MediaType.IMAGE_PNG_VALUE,
        MediaType.IMAGE_JPEG_VALUE,
        "image/svg+xml"
    })
    public Callable<ResponseEntity<byte[]>> getConnectorIcon(@PathVariable("serverType") String serverType) {
        return () -> {
            String normalized = serverType == null ? "" : serverType.trim().replaceFirst("^/+", "").toLowerCase();

            ResourceAndType icon = resolveConnectorIcon(normalized);
            ClassPathResource iconResource = icon.resource();
            MediaType contentType = icon.contentType();

            if (!iconResource.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            try {
                byte[] bytes = StreamUtils.copyToByteArray(iconResource.getInputStream());
                return ResponseEntity.ok().contentType(contentType).body(bytes);
            } catch (IOException e) {
                logger.error("Failed to read connector icon for serverType: {}", normalized, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        };
    }

    private record ResourceAndType(ClassPathResource resource, MediaType contentType) {}

    private static ResourceAndType resolveConnectorIcon(String normalizedServerType) {
        String safe = normalizedServerType == null ? "" : normalizedServerType.trim();

        // Primary location: icons/db/
        ResourceAndType icon = tryResource("icons/db/" + safe + ".jpeg", MediaType.IMAGE_JPEG);
        if (icon != null) return icon;
        icon = tryResource("icons/db/" + safe + ".jpg", MediaType.IMAGE_JPEG);
        if (icon != null) return icon;
        icon = tryResource("icons/db/" + safe + ".png", MediaType.IMAGE_PNG);
        if (icon != null) return icon;
        icon = tryResource("icons/db/" + safe + ".svg", MediaType.parseMediaType("image/svg+xml"));
        if (icon != null) return icon;

        // Fallback location: icons/
        icon = tryResource("icons/" + safe + ".jpeg", MediaType.IMAGE_JPEG);
        if (icon != null) return icon;
        icon = tryResource("icons/" + safe + ".jpg", MediaType.IMAGE_JPEG);
        if (icon != null) return icon;
        icon = tryResource("icons/" + safe + ".png", MediaType.IMAGE_PNG);
        if (icon != null) return icon;
        icon = tryResource("icons/" + safe + ".svg", MediaType.parseMediaType("image/svg+xml"));
        if (icon != null) return icon;

        // Final fallback
        return new ResourceAndType(new ClassPathResource("templates/connector-generator/icon.png"), MediaType.IMAGE_PNG);
    }

    private static ResourceAndType tryResource(String classpathLocation, MediaType contentType) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        return resource.exists() ? new ResourceAndType(resource, contentType) : null;
    }
}
