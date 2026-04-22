package com.ovaledge.csp.apps.zohodesk.main;

import com.ovaledge.csp.apps.zohodesk.client.ZohodeskClient;
import com.ovaledge.csp.apps.zohodesk.constants.ZohodeskConstants;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZohodeskMetadataService implements MetadataService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZohodeskMetadataService.class);
    private final ZohodeskClient client = new ZohodeskClient();

    @Override
    public SupportedObjectsResponse getSupportedObjects() {
        List<SupportedObject> types = new ArrayList<>();
        types.add(new SupportedObject(
                ObjectKind.ENTITY.value(),
                "Entities",
                "Zoho Desk modules such as tickets, contacts, accounts, departments, and agents"));
        types.add(new SupportedObject(ObjectKind.REPORT.value(), ZohodeskConstants.DISPLAY_NAME_REPORTS, "Zoho Desk reports"));
        return new SupportedObjectsResponse()
                .withSupportedObjects(types)
                .withSuccess(true);
    }

    @Override
    public ContainersResponse getContainers(ContainersRequest request) {
        List<ObjectInfo> containers = new ArrayList<>();
        if (request == null || request.getConnectionConfig() == null) {
            return new ContainersResponse().withContainers(containers).withSuccess(false).withMessage("Invalid connection config.");
        }
        try {
            List<Map<String, Object>> organizations = client.listEntities(
                    request.getConnectionConfig(),
                    ZohodeskConstants.ENDPOINT_ORGANIZATIONS,
                    null,
                    null,
                    false);
            for (Map<String, Object> org : organizations) {
                String id = asString(org.get("id"));
                String name = asString(org.get("name"));
                if (id.isBlank()) {
                    continue;
                }
                String finalName = name.isBlank() ? id : name;
                containers.add(new ObjectInfo(finalName, ObjectKind.CONTAINER.value())
                        .withId(id)
                        .withPath(finalName)
                        .withComment("Zoho Desk organization"));
            }
            return new ContainersResponse().withContainers(containers).withSuccess(true);
        } catch (Exception ex) {
            LOGGER.warn("Zoho Desk getContainers failed: {}", ex.getMessage());
            return new ContainersResponse().withContainers(containers).withSuccess(false)
                    .withMessage(ex.getMessage() != null ? ex.getMessage() : "Failed to fetch organizations.");
        }
    }

    @Override
    public ObjectResponse getObjects(ObjectRequest request) {
        List<ObjectInfo> objects = new ArrayList<>();
        if (request == null) {
            return new ObjectResponse().withChildren(objects).withSuccess(false).withMessage("Invalid request.");
        }
        boolean isEntityType = ObjectKind.ENTITY.value().equalsIgnoreCase(request.getEntityType());
        boolean isReportType = ObjectKind.REPORT.value().equalsIgnoreCase(request.getEntityType());
        if (!isEntityType && !isReportType) {
            return new ObjectResponse().withChildren(objects).withSuccess(true);
        }
        String containerId = request.getContainerId() != null ? request.getContainerId() : "org";
        if (isEntityType) {
            // ENTITY is a single supported object type that represents Zoho Desk modules.
            objects.add(moduleObject(ZohodeskConstants.DISPLAY_NAME_TICKETS, ZohodeskConstants.OBJECT_SUBTYPE_TICKETS, containerId));
            objects.add(moduleObject(ZohodeskConstants.DISPLAY_NAME_CONTACTS, ZohodeskConstants.OBJECT_SUBTYPE_CONTACTS, containerId));
            objects.add(moduleObject(ZohodeskConstants.DISPLAY_NAME_ACCOUNTS, ZohodeskConstants.OBJECT_SUBTYPE_ACCOUNTS, containerId));
            objects.add(moduleObject(ZohodeskConstants.DISPLAY_NAME_DEPARTMENTS, ZohodeskConstants.OBJECT_SUBTYPE_DEPARTMENTS, containerId));
            objects.add(moduleObject(ZohodeskConstants.DISPLAY_NAME_AGENTS, ZohodeskConstants.OBJECT_SUBTYPE_AGENTS, containerId));
            return new ObjectResponse().withChildren(objects).withSuccess(true);
        }
        if (request.getConnectionConfig() == null) {
            return new ObjectResponse().withChildren(objects).withSuccess(false).withMessage("Invalid connection config.");
        }
        try {
            String defaultSubtype = ZohodeskConstants.OBJECT_SUBTYPE_REPORTS;
            String subtype = resolveSubtype(request.getFilters(), defaultSubtype);
            String endpoint = endpointForSubtype(subtype);
            List<Map<String, Object>> rows = client.listEntities(
                    request.getConnectionConfig(),
                    endpoint,
                    ZohodeskConstants.MAX_PAGE_SIZE,
                    0,
                    true);
            for (Map<String, Object> row : rows) {
                String id = asString(row.get("id"));
                String name = resolveObjectName(subtype, row);
                if (id.isBlank() && name.isBlank()) {
                    continue;
                }
                String resolved = name.isBlank() ? id : name;
                objects.add(new ObjectInfo(resolved, request.getEntityType())
                        .withId(id.isBlank() ? resolved : id)
                        .withPath(containerId + "/" + resolved)
                        .withComment(subtype));
            }
            return new ObjectResponse().withChildren(objects).withSuccess(true);
        } catch (Exception ex) {
            LOGGER.warn("Zoho Desk getObjects failed: {}", ex.getMessage());
            return new ObjectResponse().withChildren(objects).withSuccess(false)
                    .withMessage(ex.getMessage() != null ? ex.getMessage() : "Failed to fetch objects.");
        }
    }

    @Override
    public FieldsResponse getFields(FieldsRequest request) {
        List<FieldInfo> fields = new ArrayList<>();
        if (request == null || request.getConnectionConfig() == null) {
            return new FieldsResponse().withFields(fields).withSuccess(false).withMessage("Invalid connection config.");
        }
        boolean isEntityType = ObjectKind.ENTITY.value().equalsIgnoreCase(request.getEntityType());
        boolean isReportType = ObjectKind.REPORT.value().equalsIgnoreCase(request.getEntityType());
        if (!isEntityType && !isReportType) {
            return new FieldsResponse().withFields(fields).withSuccess(true);
        }
        try {
            String defaultSubtype = isReportType
                    ? ZohodeskConstants.OBJECT_SUBTYPE_REPORTS
                    : subtypeFromEntityId(request.getEntityId());
            String subtype = resolveSubtype(request.getFilters(), defaultSubtype);
            String endpoint = endpointForSubtype(subtype);
            List<Map<String, Object>> rows = client.listEntities(
                    request.getConnectionConfig(),
                    endpoint,
                    1,
                    0,
                    true);
            if (rows.isEmpty()) {
                return new FieldsResponse().withFields(fields).withSuccess(true);
            }

            Map<String, Object> sample = rows.get(0);
            Set<String> orderedKeys = new LinkedHashSet<>(sample.keySet());
            int ord = 1;
            for (String key : orderedKeys) {
                Object value = sample.get(key);
                fields.add(new FieldInfo(key, inferType(value))
                        .withPosition(ord++)
                        .withNullable(value == null)
                        .withComment("Dynamic field from Zoho " + subtype));
            }
            return new FieldsResponse().withFields(fields).withSuccess(true);
        } catch (Exception ex) {
            LOGGER.warn("Zoho Desk getFields failed: {}", ex.getMessage());
            return new FieldsResponse().withFields(fields).withSuccess(false)
                    .withMessage(ex.getMessage() != null ? ex.getMessage() : "Failed to fetch fields.");
        }
    }

    static String resolveSubtype(List<Map<String, String>> filters, String defaultSubtype) {
        if (filters == null) {
            return defaultSubtype;
        }
        for (Map<String, String> filter : filters) {
            if (filter == null) {
                continue;
            }
            String sub = filter.get(ZohodeskConstants.FILTER_OBJECT_SUBTYPE);
            if (sub != null && !sub.isBlank()) {
                return sub.toLowerCase();
            }
            String displayName = filter.get("displayName");
            if (displayName != null && !displayName.isBlank()) {
                return mapDisplayNameToSubtype(displayName);
            }
        }
        return defaultSubtype;
    }

    static String mapDisplayNameToSubtype(String displayName) {
        if (displayName == null) return ZohodeskConstants.OBJECT_SUBTYPE_TICKETS;
        String value = displayName.trim().toLowerCase();
        if (value.equals("contacts")) return ZohodeskConstants.OBJECT_SUBTYPE_CONTACTS;
        if (value.equals("accounts")) return ZohodeskConstants.OBJECT_SUBTYPE_ACCOUNTS;
        if (value.equals("departments")) return ZohodeskConstants.OBJECT_SUBTYPE_DEPARTMENTS;
        if (value.equals("agents")) return ZohodeskConstants.OBJECT_SUBTYPE_AGENTS;
        if (value.equals("reports")) return ZohodeskConstants.OBJECT_SUBTYPE_REPORTS;
        return ZohodeskConstants.OBJECT_SUBTYPE_TICKETS;
    }

    static String subtypeFromEntityId(String entityId) {
        if (entityId == null || entityId.isBlank()) return ZohodeskConstants.OBJECT_SUBTYPE_TICKETS;
        String value = entityId.toLowerCase();
        if (value.contains("contact")) return ZohodeskConstants.OBJECT_SUBTYPE_CONTACTS;
        if (value.contains("account")) return ZohodeskConstants.OBJECT_SUBTYPE_ACCOUNTS;
        if (value.contains("department")) return ZohodeskConstants.OBJECT_SUBTYPE_DEPARTMENTS;
        if (value.contains("agent")) return ZohodeskConstants.OBJECT_SUBTYPE_AGENTS;
        if (value.contains("report")) return ZohodeskConstants.OBJECT_SUBTYPE_REPORTS;
        return ZohodeskConstants.OBJECT_SUBTYPE_TICKETS;
    }

    static String endpointForSubtype(String subtype) {
        if (ZohodeskConstants.OBJECT_SUBTYPE_CONTACTS.equalsIgnoreCase(subtype)) return ZohodeskConstants.ENDPOINT_CONTACTS;
        if (ZohodeskConstants.OBJECT_SUBTYPE_ACCOUNTS.equalsIgnoreCase(subtype)) return ZohodeskConstants.ENDPOINT_ACCOUNTS;
        if (ZohodeskConstants.OBJECT_SUBTYPE_DEPARTMENTS.equalsIgnoreCase(subtype)) return ZohodeskConstants.ENDPOINT_DEPARTMENTS;
        if (ZohodeskConstants.OBJECT_SUBTYPE_AGENTS.equalsIgnoreCase(subtype)) return ZohodeskConstants.ENDPOINT_AGENTS;
        if (ZohodeskConstants.OBJECT_SUBTYPE_REPORTS.equalsIgnoreCase(subtype)) return ZohodeskConstants.ENDPOINT_REPORTS;
        return ZohodeskConstants.ENDPOINT_TICKETS;
    }

    static String inferType(Object value) {
        if (value == null) return "STRING";
        if (value instanceof Integer || value instanceof Long) return "INTEGER";
        if (value instanceof Float || value instanceof Double) return "FLOAT";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof List<?>) return "ARRAY";
        if (value instanceof Map<?, ?>) return "OBJECT";
        return "STRING";
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String resolveObjectName(String subtype, Map<String, Object> row) {
        if (row == null) return "";
        List<String> candidates = new ArrayList<>();
        candidates.add("name");
        candidates.add("displayName");
        if (ZohodeskConstants.OBJECT_SUBTYPE_CONTACTS.equals(subtype) || ZohodeskConstants.OBJECT_SUBTYPE_AGENTS.equals(subtype)) {
            candidates.add("firstName");
            candidates.add("lastName");
            candidates.add("email");
        }
        if (ZohodeskConstants.OBJECT_SUBTYPE_TICKETS.equals(subtype)) {
            candidates.add("subject");
            candidates.add("ticketNumber");
        }
        if (ZohodeskConstants.OBJECT_SUBTYPE_REPORTS.equals(subtype)) {
            candidates.add("reportName");
            candidates.add("title");
        }
        for (String key : candidates) {
            String value = asString(row.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return asString(row.get("id"));
    }

    private static ObjectInfo moduleObject(String displayName, String subtype, String containerId) {
        return new ObjectInfo(displayName, ObjectKind.ENTITY.value())
                .withId(subtype)
                .withPath(containerId + "/" + displayName)
                .withComment(subtype);
    }
}
