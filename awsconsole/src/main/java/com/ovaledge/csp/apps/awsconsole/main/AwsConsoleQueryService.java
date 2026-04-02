package com.ovaledge.csp.apps.awsconsole.main;

import com.ovaledge.csp.apps.awsconsole.client.AwsConsoleClient;
import com.ovaledge.csp.apps.awsconsole.constants.AwsConsoleConstants;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import com.ovaledge.csp.v3.core.apps.model.request.QueryRequest;
import com.ovaledge.csp.v3.core.apps.model.response.QueryResponse;
import com.ovaledge.csp.v3.core.apps.service.QueryService;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Query service for AWS Console: fetches data for EC2 Instances (and other entity types).
 * Uses AwsConsoleClient for all source calls.
 */
public class AwsConsoleQueryService implements QueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AwsConsoleQueryService.class);

    @Override
    /**
     * Fetch runtime data for the specified entity type.
     *
     * For example, when entityType is EC2 Instances this returns instance metadata for the given region
     * (containerId). The method delegates to {@link AwsConsoleClient} for platform calls and returns
     * a {@link QueryResponse} containing the raw rows and an approximate total.
     *
     * @param request query request containing containerId, entityId, entityType, connection config and pagination
     * @return QueryResponse with data list, totalRows and success flag
     * @throws Exception on client or connectivity errors
     */
    public QueryResponse fetchData(QueryRequest request) throws Exception {
        String containerId = request.getContainerId();
        String entityId = request.getEntityId();
        ConnectionConfig config = request.getConnectionConfig();
        if (config == null) {
            return new QueryResponse().withData(new ArrayList<>()).withTotalRows(0).withSuccess(false);
        }
        int limit = request.getLimit() != null && request.getLimit() > 0 ? request.getLimit() : 1000;
        int offset = request.getOffset() != null && request.getOffset() >= 0 ? request.getOffset() : 0;

        try {
            config = AwsConsoleConnector.ensureConnectionConfig(config);
            AwsConsoleClient client = new AwsConsoleClient();
            String entityType = request.getEntityType();
            if (entityType == null) entityType = ObjectKind.ENTITY.value();

            List<Map<String, Object>> data;
            int totalAvailable = -1; // -1 means unknown total
            // Route to the appropriate data source based on entityType / entityId
            if (ObjectKind.FILEFOLDERS.value().equalsIgnoreCase(request.getEntityType())) {
                // S3 Buckets are global (not per-region); pagination applied in-memory
                data = client.listS3Buckets(config, limit, offset);
            } else {
                // Default: EC2 Instances — containerId = region
                data = client.listEc2Instances(config, containerId != null ? containerId : AwsConsoleConstants.DEFAULT_REGION, limit, offset);
            }
            int total = totalAvailable > 0 ? totalAvailable : data.size();
            return new QueryResponse()
                    .withData(data != null ? data : new ArrayList<>())
                    .withTotalRows(total)
                    .withSuccess(true);
        } catch (Exception e) {
            LOGGER.warn("AWS fetchData failed: {}", e.getMessage());
            String message = e.getMessage() != null ? e.getMessage() : "Query execution failed.";
            return new QueryResponse().withData(new ArrayList<>()).withTotalRows(0).withSuccess(false).withMessage(message);
        }
    }
}
