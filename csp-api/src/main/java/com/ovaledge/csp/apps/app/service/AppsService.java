package com.ovaledge.csp.apps.app.service;

import com.ovaledge.csp.v3.core.apps.model.request.ContainersRequest;
import com.ovaledge.csp.v3.core.apps.model.request.EdgiConnectorObjectRequest;
import com.ovaledge.csp.v3.core.apps.model.request.FieldsRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ObjectRequest;
import com.ovaledge.csp.v3.core.apps.model.request.QueryRequest;
import com.ovaledge.csp.v3.core.apps.model.response.*;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import com.ovaledge.csp.v3.core.apps.model.response.ValidateConnectionResponse;

/**
 * Service interface for Apps connector operations.
 * <p>
 * Provides comprehensive connector management capabilities including connection validation,
 * metadata extraction, and query execution. This service layer abstracts the complexity of
 * connector operations and provides a unified interface for all connector-related functionality.
 * </p>
 * <p>
 * <b>Key Responsibilities:</b>
 * <ul>
 *   <li><b>Connection Management:</b> Validation and configuration handling</li>
 *   <li><b>Metadata Services:</b> Supported objects, containers, objects, and fields metadata extraction</li>
 *   <li><b>Query Execution:</b> Data fetching from connectors</li>
 * </ul>
 * </p>
 * <p>
 * <b>Connector Delegation Pattern:</b>
 * <p>
 * All methods in this interface delegate to the appropriate {@link com.ovaledge.csp.v3.core.apps.service.AppsConnector}
 * implementation based on the {@code serverType} specified in the request's {@link ConnectionConfig}.
 * The connector is discovered via {@link AppsRegistry} using Java's {@link java.util.ServiceLoader} mechanism.
 * </p>
 */
public interface AppsService {
    
    // Connection validation
    
    /**
     * Validates a connector connection using the provided configuration.
     * <p>
     * Performs comprehensive connection validation including authentication,
     * network connectivity, and connector accessibility checks.
     * </p>
     *
     * @param config the connection configuration with a valid {@code serverType}
     * @return validation response with success/failure status and detailed error information
     * @throws RuntimeException if no connector is found for the specified {@code serverType}, or if connection validation
     *         fails due to network, authentication, or configuration issues. The exception message includes available connector types.
     */
    ValidateConnectionResponse validateConnection(ConnectionConfig config);
    
    // Metadata services
    
    /**
     * Retrieves supported objects (entities, reports, etc.).
     * <p>
     * Returns comprehensive information about available objects that can be queried
     * from the connector.
     * </p>
     *
     * @param config the connection configuration with a valid {@code serverType}
     * @return supported objects response with list of objects
     * @throws RuntimeException if no connector is found for the specified {@code serverType}, or if metadata cannot be retrieved
     *         due to permission or connection issues. The exception message includes available connector types.
     */
    SupportedObjectsResponse getSupportedObjects(ConnectionConfig config);
    
    /**
     * Retrieves containers (companies, organizations, etc.).
     * <p>
     * Returns comprehensive information about available containers within the connector,
     * which typically represent top-level organizational units.
     * </p>
     *
     * @param request the containers request containing connection configuration with a valid {@code serverType}
     * @return containers response with list of containers
     * @throws RuntimeException if no connector is found for the specified {@code serverType}, or if containers cannot be retrieved
     *         due to permission or connection issues. The exception message includes available connector types.
     */
    ContainersResponse getContainers(ContainersRequest request);
    
    /**
     * Retrieves objects (entities or reports) under a container.
     * <p>
     * Returns comprehensive information about objects within the specified container,
     * including entity and report listings.
     * </p>
     *
     * @param request the object request containing container identification, with connection configuration including a valid {@code serverType}
     * @return object response with list of objects
     * @throws RuntimeException if no connector is found for the specified {@code serverType}, or if objects cannot be retrieved
     *         due to permission or connection issues. The exception message includes available connector types.
     */
    ObjectResponse getObjects(ObjectRequest request);
    
    /**
     * Retrieves fields for an entity or report.
     * <p>
     * Returns detailed information about fields within the specified entity or report,
     * including field types, properties, and metadata.
     * </p>
     *
     * @param request the fields request containing entity identification, with connection configuration including a valid {@code serverType}
     * @return fields response with list of fields and their properties
     * @throws RuntimeException if no connector is found for the specified {@code serverType}, or if fields cannot be retrieved
     *         due to permission or connection issues. The exception message includes available connector types.
     */
    FieldsResponse getFields(FieldsRequest request);
    
    // Query services
    
    /**
     * Executes a query to fetch entity or report data.
     * <p>
     * Supports fetching data from connectors with proper result set handling and error management.
     * This method provides a flexible interface for executing queries with parameter support.
     * </p>
     *
     * @param request the query request containing connection configuration with a valid {@code serverType}, entity type, container, and query parameters
     * @return query response with data and execution statistics
     * @throws RuntimeException if no connector is found for the specified {@code serverType}, or if query execution fails
     *         due to syntax, permission, or connection issues. The exception message includes available connector types.
     */
    QueryResponse executeQuery(QueryRequest request);

    /**
     * Executes EDGI object processing for a connector.
     *
     * @param request EDGI object request containing connection and object context
     * @return EDGI processing response from connector
     */
    EdgiConnectorObjectResponse askEdgi(EdgiConnectorObjectRequest request);
}
