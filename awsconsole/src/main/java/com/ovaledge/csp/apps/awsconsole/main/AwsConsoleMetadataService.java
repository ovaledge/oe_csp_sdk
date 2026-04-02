package com.ovaledge.csp.apps.awsconsole.main;

import com.ovaledge.csp.apps.awsconsole.client.AwsConsoleClient;
import com.ovaledge.csp.apps.awsconsole.constants.AwsConsoleConstants;
import com.ovaledge.csp.v3.core.apps.model.FieldInfo;
import com.ovaledge.csp.v3.core.apps.model.ObjectInfo;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import com.ovaledge.csp.v3.core.apps.model.SupportedObject;
import com.ovaledge.csp.v3.core.apps.model.request.ContainersRequest;
import com.ovaledge.csp.v3.core.apps.model.request.FieldsRequest;
import com.ovaledge.csp.v3.core.apps.model.request.ObjectRequest;
import com.ovaledge.csp.v3.core.apps.model.response.ContainersResponse;
import com.ovaledge.csp.v3.core.apps.model.response.FieldsResponse;
import com.ovaledge.csp.v3.core.apps.model.response.ObjectResponse;
import com.ovaledge.csp.v3.core.apps.model.response.SupportedObjectsResponse;
import com.ovaledge.csp.v3.core.apps.service.MetadataService;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Metadata service for AWS Console: regions as containers; EC2 Instances and S3 Buckets as entity types.
 * Uses AwsConsoleClient (AWS SDK) for all source calls.
 */
public class AwsConsoleMetadataService implements MetadataService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AwsConsoleMetadataService.class);

    @Override
    public SupportedObjectsResponse getSupportedObjects() {
        List<SupportedObject> types = new ArrayList<>();
        types.add(new SupportedObject(ObjectKind.ENTITY.value(), AwsConsoleConstants.DISPLAY_NAME_EC2_INSTANCES,
                "EC2 instances in the selected region"));
        types.add(new SupportedObject(ObjectKind.FILEFOLDERS.value(), AwsConsoleConstants.DISPLAY_NAME_S3_BUCKETS,
                "S3 buckets (global list)"));
        return new SupportedObjectsResponse()
                .withSupportedObjects(types)
                .withSuccess(true);
    }

    @Override
    /**
     * Return the list of AWS containers (regions) available for the provided connection.
     *
     * The connection config must include valid credentials (access key/secret). On success the
     * method returns a ContainersResponse with region ids as ObjectInfo entries.
     *
     * @param request request containing connection config and optional filters
     * @return ContainersResponse containing a list of regions and success flag
     */
    public ContainersResponse getContainers(ContainersRequest request) {
        List<ObjectInfo> containers = new ArrayList<>();
        ConnectionConfig config = request.getConnectionConfig();
        if (config == null) {
            return new ContainersResponse().withContainers(containers).withSuccess(false).withMessage("Invalid connection config.");
        }
        try {
            config = AwsConsoleConnector.ensureConnectionConfig(config);
            List<String> regions = new AwsConsoleClient().listRegions(config);
            for (String name : regions) {
                containers.add(new ObjectInfo(name, ObjectKind.CONTAINER.value())
                        .withId(name)
                        .withPath(name)
                        .withComment("AWS Region: " + name));
            }
            return new ContainersResponse().withContainers(containers).withSuccess(true);
        } catch (Exception e) {
            LOGGER.warn("AWS getContainers failed: {}", e.getMessage());
            String message = e.getMessage() != null ? e.getMessage() : "Failed to list AWS regions.";
            return new ContainersResponse().withContainers(containers).withSuccess(false).withMessage(message);
        }
    }

    @Override
    /**
     * Return the list of objects for the given container and entity type.
     *
     * For AWS Console this maps to resource lists such as EC2 instances for a region.
     * The implementation delegates to AwsConsoleClient for API calls and maps results to ObjectInfo.
     *
     * @param request request containing containerId (region), entityType and connection config
     * @return ObjectResponse with discovered objects and success flag
     */
    public ObjectResponse getObjects(ObjectRequest request) {
        List<ObjectInfo> objects = new ArrayList<>();
        String containerId = request.getContainerId();
        String entityType = request.getEntityType();
        if (containerId == null || containerId.isBlank() || entityType == null || entityType.isBlank()) {
            return new ObjectResponse().withChildren(objects).withSuccess(true);
        }
        ConnectionConfig config = request.getConnectionConfig();
        if (config == null) {
            return new ObjectResponse().withChildren(objects).withSuccess(true);
        }
        try {
            config = AwsConsoleConnector.ensureConnectionConfig(config);
            AwsConsoleClient client = new AwsConsoleClient();
            String displayName = resolveDisplayNameFromRequest(request);
            if (AwsConsoleConstants.DISPLAY_NAME_EC2_INSTANCES.equals(displayName)) {
                List<Map<String, Object>> rows = client.listEc2Instances(config, containerId, 500, 0);
                for (Map<String, Object> row : rows) {
                    String instanceId = String.valueOf(row.get("instanceId"));
                    String instanceType = String.valueOf(row.get("instanceType"));
                    String state = String.valueOf(row.get("state"));
                    objects.add(new ObjectInfo(instanceId, entityType)
                            .withId(instanceId)
                            .withPath(containerId + "/" + instanceId)
                            .withComment(state + " - " + instanceType));
                }
            } else if (AwsConsoleConstants.DISPLAY_NAME_S3_BUCKETS.equals(displayName)) {
                List<Map<String, Object>> bucketRows = client.listS3Buckets(config, 500, 0);
                for (Map<String, Object> row : bucketRows) {
                    String bucketName = String.valueOf(row.get("bucketName"));
                    String creationDate = String.valueOf(row.get("creationDate"));
                    objects.add(new ObjectInfo(bucketName, entityType)
                            .withId(bucketName)
                            .withPath(bucketName)
                            .withComment("Created: " + creationDate));
                }
            }
            return new ObjectResponse().withChildren(objects).withSuccess(true);
        } catch (Exception e) {
            LOGGER.warn("AWS getObjects failed: {}", e.getMessage());
            String message = e.getMessage() != null ? e.getMessage() : "Failed to list AWS objects.";
            return new ObjectResponse().withChildren(objects).withSuccess(false).withMessage(message);
        }
    }

    @Override
    /**
     * Return the field/column metadata for the requested entity type.
     *
     * Example: for EC2 instances this returns fields like instanceId, instanceType, state and region.
     *
     * @param request request containing entityType and optional parameters
     * @return FieldsResponse with field metadata for use in the UI/query builder
     */
    public FieldsResponse getFields(FieldsRequest request) {
        List<FieldInfo> fields = new ArrayList<>();
        String entityType = request.getEntityType();
        String entityId = request.getEntityId();
        if (entityType == null || entityType.isBlank()) {
            return new FieldsResponse().withFields(fields).withSuccess(true);
        }
        // EC2 instance fields
        if (ObjectKind.ENTITY.value().equalsIgnoreCase(request.getEntityType())) {
            int ord = 1;
            fields.add(new FieldInfo("instanceId", "STRING").withPosition(ord++).withNullable(false).withComment("EC2 instance ID"));
            fields.add(new FieldInfo("instanceType", "STRING").withPosition(ord++).withNullable(true).withComment("Instance type"));
            fields.add(new FieldInfo("state", "STRING").withPosition(ord++).withNullable(true).withComment("Instance state"));
            fields.add(new FieldInfo("region", "STRING").withPosition(ord).withNullable(true).withComment("AWS region"));
        } else if (ObjectKind.FILEFOLDERS.value().equalsIgnoreCase(request.getEntityType())) {
            // S3 bucket fields
            int ord = 1;
            fields.add(new FieldInfo("bucketName", "STRING").withPosition(ord++).withNullable(false).withComment("S3 bucket name"));
            fields.add(new FieldInfo("creationDate", "STRING").withPosition(ord).withNullable(true).withComment("Bucket creation timestamp"));
        }
        return new FieldsResponse().withFields(fields).withSuccess(true);
    }

    private static String resolveDisplayNameFromRequest(ObjectRequest request) {
        List<Map<String, String>> filterList = request.getFilters();
        if (filterList != null && !filterList.isEmpty()) {
            for (Map<String, String> filters : filterList) {
                if (filters != null && filters.containsKey(AwsConsoleConstants.FILTER_OBJECT_SUBTYPE)) {
                    String sub = filters.get(AwsConsoleConstants.FILTER_OBJECT_SUBTYPE);
                    if (AwsConsoleConstants.OBJECT_SUBTYPE_EC2_INSTANCES.equals(sub)) return AwsConsoleConstants.DISPLAY_NAME_EC2_INSTANCES;
                    if (AwsConsoleConstants.OBJECT_SUBTYPE_S3_BUCKETS.equals(sub)) return AwsConsoleConstants.DISPLAY_NAME_S3_BUCKETS;
                }
                if (filters != null && filters.containsKey("displayName")) {
                    return filters.get("displayName");
                }
            }
        }
        return AwsConsoleConstants.DISPLAY_NAME_EC2_INSTANCES;
    }
}
