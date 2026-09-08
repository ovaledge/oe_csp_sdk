package com.ovaledge.csp.apps.app.service;

import com.ovaledge.csp.v3.core.apps.model.request.ContainersRequest;
import com.ovaledge.csp.v3.core.apps.model.request.EdgiConnectorObjectRequest;
import com.ovaledge.csp.v3.core.apps.model.request.FieldsRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ObjectRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileBatchRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileColumnRequest;
import com.ovaledge.csp.v3.core.apps.model.request.FileProfileRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ProfileRowCountRequest;
import com.ovaledge.csp.v3.core.apps.model.request.QueryRequest;
import com.ovaledge.csp.v3.core.apps.model.request.SampleProfileRequest;
import com.ovaledge.csp.v3.core.apps.model.response.*;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import com.ovaledge.csp.v3.core.apps.model.response.ValidateConnectionResponse;

import java.util.Map;

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
 *   <li><b>Profiling:</b> Row count, column, sample, file, and batch via {@code ProfilingService}</li>
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
     * Returns row count via the connector's {@link com.ovaledge.csp.v3.core.apps.service.ProfilingService}.
     *
     * @param request identity ({@code objectKind}, container, entity) and connection
     * @return row count
     * @throws com.ovaledge.csp.v3.core.apps.exceptions.ProfilingUnsupportedException
     *         if the connector has no profiling service, or the operation/kind is unsupported
     * @throws com.ovaledge.csp.v3.core.apps.exceptions.ProfilingException on hard profiling failures
     * @throws RuntimeException if no connector is found for {@code serverType}
     */
    long getRowCount(ProfileRowCountRequest request);

    /**
     * Profiles a single column via {@link com.ovaledge.csp.v3.core.apps.service.ProfilingService}.
     *
     * @param request column identity plus {@code dataType}/{@code dataLength} for skip rules
     * @return per-column stats
     * @throws com.ovaledge.csp.v3.core.apps.exceptions.ProfilingUnsupportedException
     *         if the connector has no profiling service, or the operation/kind is unsupported
     * @throws com.ovaledge.csp.v3.core.apps.exceptions.ProfilingException on hard profiling failures
     * @throws RuntimeException if no connector is found for {@code serverType}
     */
    ProfileColumnResult profileColumn(ProfileColumnRequest request);

    /**
     * Sample-profiles an object via {@link com.ovaledge.csp.v3.core.apps.service.ProfilingService}.
     *
     * @param request object identity plus optional fields and sample size
     * @return field name → stats
     * @throws com.ovaledge.csp.v3.core.apps.exceptions.ProfilingUnsupportedException
     *         if the connector has no profiling service, or the operation/kind is unsupported
     * @throws com.ovaledge.csp.v3.core.apps.exceptions.ProfilingException on hard profiling failures
     * @throws RuntimeException if no connector is found for {@code serverType}
     */
    Map<String, ProfileColumnResult> sampleProfile(SampleProfileRequest request);

    /**
     * Batch-profiles targets via {@link com.ovaledge.csp.v3.core.apps.service.ProfilingService}.
     * {@code batchMode} selects row-count, column, sample, or both.
     *
     * @param request shared identity plus {@code targets} and {@code batchMode}
     * @return per-entity row counts and/or column results, plus failure maps
     * @throws com.ovaledge.csp.v3.core.apps.exceptions.ProfilingUnsupportedException
     *         if the connector has no profiling service, or the operation/kind is unsupported
     * @throws com.ovaledge.csp.v3.core.apps.exceptions.ProfilingException on hard profiling failures
     * @throws RuntimeException if no connector is found for {@code serverType}
     */
    ProfileBatchResponse profileBatch(ProfileBatchRequest request);

    /**
     * File profiling via {@link com.ovaledge.csp.v3.core.apps.service.ProfilingService}.
     * Default connector implementations throw unsupported; live file parse is connector-owned.
     *
     * @param request file identity ({@code objectKind=FILE}, location, optional sheet/headers)
     * @return file profile response (column stats by sheet when implemented)
     * @throws com.ovaledge.csp.v3.core.apps.exceptions.ProfilingUnsupportedException
     *         if the connector has no profiling service, or file profiling is unsupported
     * @throws com.ovaledge.csp.v3.core.apps.exceptions.ProfilingException on hard profiling failures
     * @throws RuntimeException if no connector is found for {@code serverType}
     */
    FileProfileResponse profileFile(FileProfileRequest request);

    /**
     * Executes EDGI object processing for a connector.
     *
     * @param request EDGI object request containing connection and object context
     * @return EDGI processing response from connector
     */
    EdgiConnectorObjectResponse askEdgi(EdgiConnectorObjectRequest request);
}
