package com.ovaledge.csp.apps.app.service;

import com.ovaledge.csp.v3.core.apps.model.request.ContainersRequest;
import com.ovaledge.csp.v3.core.apps.model.request.EdgiConnectorObjectRequest;
import com.ovaledge.csp.v3.core.apps.model.request.FieldsRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ObjectRequest;
import com.ovaledge.csp.v3.core.apps.model.request.QueryRequest;
import com.ovaledge.csp.v3.core.apps.model.response.*;
import com.ovaledge.csp.v3.core.apps.service.AppsConnector;
import com.ovaledge.csp.v3.core.apps.service.MetadataService;
import com.ovaledge.csp.v3.core.apps.service.QueryService;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import com.ovaledge.csp.v3.core.apps.model.response.ValidateConnectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Implementation of the AppsService interface.
 * <p>
 * Provides concrete implementation of all connector operations including connection validation,
 * metadata extraction, and query execution. This implementation delegates to appropriate Apps connectors
 * based on the server type specified in the requests, using AppsRegistry for discovery.
 * </p>
 */
@Service
public class AppsServiceImpl implements AppsService {
    
    private static final Logger logger = LoggerFactory.getLogger(AppsServiceImpl.class);
    
    @Autowired
    private AppsRegistry appsRegistry;
    
    private AppsConnector getConnector(String serverType) {
        if (serverType == null || serverType.isEmpty()) {
            throw new RuntimeException("Server type is missing from request");
        }
        AppsConnector connector = appsRegistry.getConnector(serverType);
        if (connector == null) {
            throw new RuntimeException("No connector found for serverType: " + serverType + 
                ". Available types: " + appsRegistry.getAvailableServerTypes());
        }
        return connector;
    }
    
    @Override
    public ValidateConnectionResponse validateConnection(ConnectionConfig config) {
        try {
            AppsConnector connector = getConnector(config.getServerType());
            return connector.validateConnection(config);
        } catch (Exception e) {
            logger.error("Failed to validate connection: {}", e.getMessage(), e);
            throw new RuntimeException("Connection validation failed", e);
        }
    }
    
    @Override
    public SupportedObjectsResponse getSupportedObjects(ConnectionConfig config) {
        try {
            AppsConnector connector = getConnector(config.getServerType());
            MetadataService metadataService = connector.getMetadataService();
            return metadataService.getSupportedObjects();
        } catch (Exception e) {
            logger.error("Failed to get supported objects: {}", e.getMessage(), e);
            throw new RuntimeException("Supported objects retrieval failed", e);
        }
    }
    
    @Override
    public ContainersResponse getContainers(ContainersRequest request) {
        try {
            AppsConnector connector = getConnector(request.getConnectionConfig().getServerType());
            MetadataService metadataService = connector.getMetadataService();
            return metadataService.getContainers(request);
        } catch (Exception e) {
            logger.error("Failed to get containers: {}", e.getMessage(), e);
            throw new RuntimeException("Containers retrieval failed", e);
        }
    }
    
    @Override
    public ObjectResponse getObjects(ObjectRequest request) {
        try {
            AppsConnector connector = getConnector(request.getConnectionConfig().getServerType());
            MetadataService metadataService = connector.getMetadataService();
            return metadataService.getObjects(request);
        } catch (Exception e) {
            logger.error("Failed to get objects: {}", e.getMessage(), e);
            throw new RuntimeException("Objects retrieval failed", e);
        }
    }
    
    @Override
    public FieldsResponse getFields(FieldsRequest request) {
        try {
            AppsConnector connector = getConnector(request.getConnectionConfig().getServerType());
            MetadataService metadataService = connector.getMetadataService();
            return metadataService.getFields(request);
        } catch (Exception e) {
            logger.error("Failed to get fields: {}", e.getMessage(), e);
            throw new RuntimeException("Fields retrieval failed", e);
        }
    }
    
    @Override
    public QueryResponse executeQuery(QueryRequest request) {
        try {
            AppsConnector connector = getConnector(request.getConnectionConfig().getServerType());
            QueryService queryService = connector.getQueryService();
            return queryService.fetchData(request);
        } catch (Exception e) {
            logger.error("Failed to execute query: {}", e.getMessage(), e);
            throw new RuntimeException("Query execution failed", e);
        }
    }

    @Override
    public EdgiConnectorObjectResponse askEdgi(EdgiConnectorObjectRequest request) {
        try {
            if (request == null || request.getConnInfo() == null || request.getConnInfo().toConnectionConfig() == null) {
                throw new RuntimeException("ConnInfo is required to process askEdgi request");
            }
            AppsConnector connector = getConnector(request.getConnInfo().toConnectionConfig().getServerType());
            return connector.processAppObjectsForEdgi(request);
        } catch (Exception e) {
            logger.error("Failed to process askEdgi request: {}", e.getMessage(), e);
            throw new RuntimeException("askEdgi execution failed", e);
        }
    }
}
