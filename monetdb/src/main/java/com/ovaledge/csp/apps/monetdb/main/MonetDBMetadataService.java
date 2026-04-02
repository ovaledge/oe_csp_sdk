package com.ovaledge.csp.apps.monetdb.main;

import com.ovaledge.csp.apps.monetdb.constants.MonetDBConstants;
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
import com.ovaledge.csp.v3.core.connectionpool.core.ConnectionPoolManager;
import com.ovaledge.csp.v3.core.connectionpool.core.ConnectionResource;
import com.ovaledge.csp.v3.core.connectionpool.enums.ResourceType;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import com.ovaledge.csp.v3.core.apps.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Metadata service for MonetDB: schemas as containers; tables, views, functions, sequences, indexes, triggers as objects.
 * Uses ConnectionConfig as-is. No Client; JDBC operations are inlined.
 * Object types use ENTITY; getObjects uses request filters (objectSubType/displayName) to distinguish.
 * entityId uses optional prefixes (func:, seq:, idx:, trg:) so getFields can dispatch without filters.
 */
public class MonetDBMetadataService implements MetadataService {

    private static final Logger LOG = LoggerFactory.getLogger(MonetDBMetadataService.class);

    @Override
    public SupportedObjectsResponse getSupportedObjects() {
        List<SupportedObject> types = new ArrayList<>();
        types.add(new SupportedObject(ObjectKind.ENTITY.value(), MonetDBConstants.DISPLAY_NAME_TABLES, "MonetDB database tables"));
        types.add(new SupportedObject(ObjectKind.ENTITY.value(), MonetDBConstants.DISPLAY_NAME_VIEWS, "MonetDB views (entities)"));
        types.add(new SupportedObject(ObjectKind.FUNCTION.value(), MonetDBConstants.DISPLAY_NAME_FUNCTIONS, "MonetDB functions and procedures"));
        types.add(new SupportedObject(ObjectKind.SEQUENCE.value(), MonetDBConstants.DISPLAY_NAME_SEQUENCES, "MonetDB sequences"));
        types.add(new SupportedObject(ObjectKind.INDEX.value(), MonetDBConstants.DISPLAY_NAME_INDEXES, "MonetDB indexes"));
        types.add(new SupportedObject(ObjectKind.TRIGGER.value(), MonetDBConstants.DISPLAY_NAME_TRIGGERS, "MonetDB triggers"));
        return new SupportedObjectsResponse()
                .withSupportedObjects(types)
                .withSuccess(true);
    }

    @Override
    /**
     * Return the list of containers (schemas) available in the target MonetDB instance.
     *
     * The connector will use the provided ConnectionConfig to query the database for schemas.
     * Each returned ObjectInfo will contain id, path and an optional comment.
     *
     * @param request request containing connection config and optional filters
     * @return ContainersResponse with the list of containers and success flag
     */
    public ContainersResponse getContainers(ContainersRequest request) {
        List<ObjectInfo> containers = new ArrayList<>();
        ConnectionConfig config = request.getConnectionConfig();
        if (config == null) {
            return new ContainersResponse().withContainers(containers).withSuccess(false);
        }
        try {
            config = MonetDBConnector.ensureConnectionConfig(config);
            ConnectionResource resource = (ConnectionResource) ConnectionPoolManager.getInstance()
                    .getOrCreateResource(config, ResourceType.JDBC);
            String sql = "SELECT name FROM sys.schemas WHERE name NOT IN ('tmp') ORDER BY name";
            List<String> schemas = resource.queryForList(sql, "getContainers", false, String.class);
            for (String name : schemas) {
                containers.add(new ObjectInfo(name, ObjectKind.CONTAINER.value())
                        .withId(name)
                        .withPath(name)
                        .withComment("Schema: " + name));
            }
        } catch (Exception e) {
            LOG.warn("MonetDB getContainers failed: {}", e.getMessage());
            return new ContainersResponse().withContainers(containers).withSuccess(false);
        }
        return new ContainersResponse().withContainers(containers).withSuccess(true);
    }

    @Override
    /**
     * Return the list of objects (tables, views, functions, sequences, indexes, triggers) for the given container.
     *
     * The request specifies containerId and entityType; this method routes to the appropriate fetch*
     * helper depending on subtype and request filters.
     *
     * @param request request containing containerId, entityType, connection config and filters
     * @return ObjectResponse with the matching objects and success flag
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
            config = MonetDBConnector.ensureConnectionConfig(config);
            ConnectionResource resource = (ConnectionResource) ConnectionPoolManager.getInstance()
                    .getOrCreateResource(config, ResourceType.JDBC);

            String subType = "";
            if (request != null && request.getFilters() != null) {
                for (Map<String, String> filter : request.getFilters()) {
                    if (filter == null) continue;
                    subType = filter.get("displayName");
                }
            }

            switch (subType) {
                case MonetDBConstants.DISPLAY_NAME_VIEWS:
                    fetchViewObjects(resource, containerId, entityType, objects);
                    break;
                case MonetDBConstants.DISPLAY_NAME_FUNCTIONS:
                    fetchFunctionObjects(resource, containerId, entityType, objects);
                    break;
                case MonetDBConstants.DISPLAY_NAME_SEQUENCES:
                    fetchSequenceObjects(resource, containerId, entityType, objects);
                    break;
                case MonetDBConstants.DISPLAY_NAME_INDEXES:
                    fetchIndexObjects(resource, containerId, entityType, objects);
                    break;
                case MonetDBConstants.DISPLAY_NAME_TRIGGERS:
                    fetchTriggerObjects(resource, containerId, entityType, objects);
                    break;
                default:
                    fetchTableObjects(resource, containerId, entityType, objects);
                    break;
            }
        } catch (Exception e) {
            LOG.warn("MonetDB getObjects failed: {}", e.getMessage());
            return new ObjectResponse().withChildren(objects).withSuccess(false);
        }
        return new ObjectResponse().withChildren(objects).withSuccess(true);
    }

    private void fetchTableObjects(ConnectionResource resource, String containerId, String entityType, List<ObjectInfo> objects) {
        String sql = "SELECT t.name FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.id "
                + "JOIN sys.table_types tt ON t.type = tt.table_type_id WHERE s.name = ? AND LOWER(tt.table_type_name) = ? ORDER BY t.name";
        List<String> names;
        try {
            names = resource.queryForList(sql, "fetchTableObjects", false, String.class, containerId, MonetDBConstants.OBJECT_SUBTYPE_TABLE);
        } catch (Exception e) {
            String fallback = "SELECT t.name FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.id WHERE s.name = ? ORDER BY t.name";
            try {
                names = resource.queryForList(fallback, "fetchTableObjectsFallback", false, String.class, containerId);
            } catch (SQLException e2) {
                LOG.debug("MonetDB fetchTableObjects fallback failed: {}", e2.getMessage());
                names = new ArrayList<>();
            }
        }
        for (String name : names) {
            objects.add(new ObjectInfo(name, entityType).withId(name).withPath(containerId + "/" + name).withComment(containerId + "." + name));
        }
    }

    private void fetchViewObjects(ConnectionResource resource, String containerId, String entityType, List<ObjectInfo> objects) {
        try {
            String sql = "SELECT t.name FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.id "
                    + "JOIN sys.table_types tt ON t.type = tt.table_type_id WHERE s.name = ? AND LOWER(tt.table_type_name) = ? ORDER BY t.name";
            List<String> names = resource.queryForList(sql, "fetchViewObjects", false, String.class, containerId, MonetDBConstants.OBJECT_SUBTYPE_VIEW);
            for (String name : names) {
                objects.add(new ObjectInfo(name, entityType).withId(name).withPath(containerId + "/" + name).withComment(containerId + "." + name));
            }
        } catch (Exception e) {
            LOG.debug("MonetDB fetchViewObjects: {}", e.getMessage());
        }
    }

    private void fetchFunctionObjects(ConnectionResource resource, String containerId, String entityType, List<ObjectInfo> objects) {
        String sql = "SELECT f.id, f.name FROM sys.functions f JOIN sys.schemas s ON f.schema_id = s.id "
                + "WHERE s.name = ? AND (f.system = false OR f.system IS NULL) ORDER BY f.name, f.id";
        try {
            List<Map<String, Object>> rows = resource.queryForList(sql, "fetchFunctionObjects", false, containerId);
            for (Map<String, Object> row : rows) {
                String name = Utils.getString(row, "name");
                Object idObj = row.get("id");
                String id = (idObj != null ? idObj.toString() : null);
                String objectId = id != null ? MonetDBConstants.ENTITY_ID_PREFIX_FUNCTION + name + "#" + id : MonetDBConstants.ENTITY_ID_PREFIX_FUNCTION + name;
                objects.add(new ObjectInfo(name, entityType).withId(objectId).withPath(containerId + "/" + name).withComment(containerId + "." + name));
            }
        } catch (Exception e) {
            LOG.debug("MonetDB fetchFunctionObjects: {}", e.getMessage());
        }
    }

    private void fetchSequenceObjects(ConnectionResource resource, String containerId, String entityType, List<ObjectInfo> objects) {
        String sql = "SELECT seq.name FROM sys.sequences seq JOIN sys.schemas s ON seq.schema_id = s.id WHERE s.name = ? ORDER BY seq.name";
        try {
            List<String> names = resource.queryForList(sql, "fetchSequenceObjects", false, String.class, containerId);
            for (String name : names) {
                String objectId = MonetDBConstants.ENTITY_ID_PREFIX_SEQUENCE + name;
                objects.add(new ObjectInfo(name, entityType).withId(objectId).withPath(containerId + "/" + name).withComment(containerId + "." + name));
            }
        } catch (Exception e) {
            LOG.debug("MonetDB fetchSequenceObjects: {}", e.getMessage());
        }
    }

    private void fetchIndexObjects(ConnectionResource resource, String containerId, String entityType, List<ObjectInfo> objects) {
        String sql = "SELECT t.name AS tablename, i.name AS idxname FROM sys.idxs i "
                + "JOIN sys.tables t ON i.table_id = t.id JOIN sys.schemas s ON t.schema_id = s.id WHERE s.name = ? ORDER BY t.name, i.name";
        try {
            List<Map<String, Object>> rows = resource.queryForList(sql, "fetchIndexObjects", false, containerId);
            for (Map<String, Object> row : rows) {
                String tablename = Utils.getString(row, "tablename");
                String idxname = Utils.getString(row, "idxname");
                if (tablename == null || idxname == null) continue;
                String composite = tablename + "." + idxname;
                String objectId = MonetDBConstants.ENTITY_ID_PREFIX_INDEX + composite;
                objects.add(new ObjectInfo(composite, entityType).withId(objectId).withPath(containerId + "/" + composite).withComment(containerId + "." + composite));
            }
        } catch (Exception e) {
            LOG.debug("MonetDB fetchIndexObjects: {}", e.getMessage());
        }
    }

    private void fetchTriggerObjects(ConnectionResource resource, String containerId, String entityType, List<ObjectInfo> objects) {
        String sql = "SELECT t.name AS tablename, tr.name AS trigname FROM sys.triggers tr "
                + "JOIN sys.tables t ON tr.table_id = t.id JOIN sys.schemas s ON t.schema_id = s.id WHERE s.name = ? ORDER BY t.name, tr.name";
        try {
            List<Map<String, Object>> rows = resource.queryForList(sql, "fetchTriggerObjects", false, containerId);
            for (Map<String, Object> row : rows) {
                String tablename = Utils.getString(row, "tablename");
                String trigname = Utils.getString(row, "trigname");
                if (tablename == null || trigname == null) continue;
                String composite = tablename + "." + trigname;
                String objectId = MonetDBConstants.ENTITY_ID_PREFIX_TRIGGER + composite;
                objects.add(new ObjectInfo(composite, entityType).withId(objectId).withPath(containerId + "/" + composite).withComment(containerId + "." + composite));
            }
        } catch (Exception e) {
            LOG.debug("MonetDB fetchTriggerObjects: {}", e.getMessage());
        }
    }

    @Override
    /**
     * Return the fields/columns metadata for the requested object (table/view/function/sequence/index/trigger).
     *
     * The method inspects entityType and entityId to dispatch to the appropriate helper to collect
     * FieldInfo entries describing name, type, position and nullability.
     *
     * @param request request containing containerId, entityId, entityType and connection config
     * @return FieldsResponse with a list of FieldInfo and success flag
     */
    public FieldsResponse getFields(FieldsRequest request) {
        List<FieldInfo> fields = new ArrayList<>();
        String containerId = request.getContainerId();
        String entityId = request.getEntityId();
        if (containerId == null || containerId.isBlank() || entityId == null || entityId.isBlank()) {
            return new FieldsResponse().withFields(fields).withSuccess(true);
        }
        ConnectionConfig config = request.getConnectionConfig();
        if (config == null) {
            return new FieldsResponse().withFields(fields).withSuccess(true);
        }
        try {
            config = MonetDBConnector.ensureConnectionConfig(config);
            ConnectionResource resource = (ConnectionResource) ConnectionPoolManager.getInstance()
                    .getOrCreateResource(config, ResourceType.JDBC);
            String entityType = request.getEntityType();

            // Same discriminator strategy as getObjects(): MonetDB uses displayName to distinguish table vs view.
            String displayName = null;
            if (request.getFilters() != null) {
                for (Map<String, String> filter : request.getFilters()) {
                    if (filter == null) continue;
                    displayName = filter.get("displayName");
                }
            }

            String objectSubtype = null;
            if (displayName != null) {
                if (MonetDBConstants.DISPLAY_NAME_VIEWS.equalsIgnoreCase(displayName)) {
                    objectSubtype = MonetDBConstants.OBJECT_SUBTYPE_VIEW;
                } else if (MonetDBConstants.DISPLAY_NAME_TABLES.equalsIgnoreCase(displayName)) {
                    objectSubtype = MonetDBConstants.OBJECT_SUBTYPE_TABLE;
                }
            }

            if (ObjectKind.FUNCTION.value().equalsIgnoreCase(entityType != null ? entityType : "")) {
                getFieldsForFunction(resource, containerId, entityId, fields);
            } else if (ObjectKind.SEQUENCE.value().equalsIgnoreCase(entityType != null ? entityType : "")) {
                getFieldsForSequence(resource, containerId, entityId, fields);
            } else if (ObjectKind.INDEX.value().equalsIgnoreCase(entityType != null ? entityType : "")) {
                getFieldsForIndex(resource, containerId, entityId, fields);
            } else if (ObjectKind.TRIGGER.value().equalsIgnoreCase(entityType != null ? entityType : "")) {
                getFieldsForTrigger(resource, containerId, entityId, fields);
            } else if (entityId.startsWith(MonetDBConstants.ENTITY_ID_PREFIX_FUNCTION)) {
                getFieldsForFunction(resource, containerId, entityId, fields);
            } else if (entityId.startsWith(MonetDBConstants.ENTITY_ID_PREFIX_SEQUENCE)) {
                getFieldsForSequence(resource, containerId, entityId, fields);
            } else if (entityId.startsWith(MonetDBConstants.ENTITY_ID_PREFIX_INDEX)) {
                getFieldsForIndex(resource, containerId, entityId, fields);
            } else if (entityId.startsWith(MonetDBConstants.ENTITY_ID_PREFIX_TRIGGER)) {
                getFieldsForTrigger(resource, containerId, entityId, fields);
            } else {
                getFieldsForTableOrView(resource, containerId, entityId, fields, objectSubtype);
            }
        } catch (Exception e) {
            LOG.warn("MonetDB getFields failed: {}", e.getMessage());
            return new FieldsResponse().withFields(fields).withSuccess(false)
                    .withMessage("Failed to get fields: " + e.getMessage());
        }
        return new FieldsResponse().withFields(fields).withSuccess(true);
    }

    private void getFieldsForTableOrView(
            ConnectionResource resource,
            String containerId,
            String entityId,
            List<FieldInfo> fields,
            String objectSubtype) {
        String baseSql = "SELECT c.name AS column_name, c.type AS type_name, c.\"number\" AS position, c.\"null\" AS nullable "
                + "FROM sys.columns c JOIN sys.tables t ON c.table_id = t.id "
                + "JOIN sys.schemas s ON t.schema_id = s.id "
                + "JOIN sys.table_types tt ON t.type = tt.table_type_id "
                + "WHERE s.name = ? AND t.name = ?";

        String sql = objectSubtype != null
                ? baseSql + " AND LOWER(tt.table_type_name) = ? ORDER BY c.\"number\""
                : baseSql + " ORDER BY c.\"number\"";
        List<Map<String, Object>> rows;
        try {
            if (objectSubtype != null) {
                rows = resource.queryForList(sql, "getFieldsForTableOrView", false, containerId, entityId, objectSubtype);
            } else {
                rows = resource.queryForList(sql, "getFieldsForTableOrView", false, containerId, entityId);
            }
        } catch (SQLException e) {
            LOG.debug("MonetDB getFieldsForTableOrView failed: {}", e.getMessage());
            return;
        }
        for (Map<String, Object> row : rows) {
            String colName = Utils.getString(row, "column_name");
            String typeName = Utils.getString(row, "type_name");
            Integer pos = Utils.getInteger(row, "position");
            Object nullFlag = row.get("nullable");
            boolean nullable = nullFlag == null || Boolean.TRUE.equals(nullFlag)
                    || "1".equals(String.valueOf(nullFlag)) || "true".equalsIgnoreCase(String.valueOf(nullFlag));
            fields.add(new FieldInfo(colName, typeName != null ? typeName : "VARCHAR")
                    .withPosition(pos != null ? pos : 0)
                    .withNullable(nullable));
        }
    }

    private void getFieldsForFunction(ConnectionResource resource, String containerId, String entityId, List<FieldInfo> fields) {
        String payload = entityId.substring(MonetDBConstants.ENTITY_ID_PREFIX_FUNCTION.length());
        String funcName;
        Integer funcId = null;
        int hash = payload.indexOf('#');
        if (hash >= 0) {
            funcName = payload.substring(0, hash);
            try {
                funcId = Integer.parseInt(payload.substring(hash + 1));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        } else {
            funcName = payload;
        }
        String sql = "SELECT a.name AS arg_name, a.type AS type_name, a.\"number\" AS position, a.inout "
                + "FROM sys.args a JOIN sys.functions f ON a.func_id = f.id JOIN sys.schemas s ON f.schema_id = s.id "
                + "WHERE s.name = ? AND f.name = ? ";
        List<Map<String, Object>> rows;
        try {
            if (funcId != null) {
                rows = resource.queryForList(sql + "AND f.id = ? ORDER BY a.\"number\"", "getFieldsForFunction", false, containerId, funcName, funcId);
            } else {
                rows = resource.queryForList(sql + "ORDER BY a.\"number\"", "getFieldsForFunction", false, containerId, funcName);
            }
        } catch (SQLException e) {
            LOG.debug("MonetDB getFieldsForFunction failed: {}", e.getMessage());
            return;
        }
        if (rows == null) rows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String argName = Utils.getString(row, "arg_name");
            String typeName = Utils.getString(row, "type_name");
            Integer pos = Utils.getInteger(row, "position");
            Object inoutObj = row.get("inout");
            boolean out = inoutObj != null && (Integer.valueOf(1).equals(inoutObj) || "1".equals(String.valueOf(inoutObj)));
            String name = argName != null ? argName : ("#" + pos);
            fields.add(new FieldInfo(name, typeName != null ? typeName : "VARCHAR").withPosition(pos != null ? pos : 0).withNullable(!out));
        }
    }

    private void getFieldsForSequence(ConnectionResource resource, String containerId, String entityId, List<FieldInfo> fields) {
        String name = entityId.substring(MonetDBConstants.ENTITY_ID_PREFIX_SEQUENCE.length());
        String sql = "SELECT start, minvalue, maxvalue, increment, cycle FROM sys.sequences seq "
                + "JOIN sys.schemas s ON seq.schema_id = s.id WHERE s.name = ? AND seq.name = ?";
        try {
            List<Map<String, Object>> rows = resource.queryForList(sql, "getFieldsForSequence", false, containerId, name);
            if (rows != null && !rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                int pos = 0;
                addPropField(fields, "start", row.get("start"), pos++);
                addPropField(fields, "minvalue", row.get("minvalue"), pos++);
                addPropField(fields, "maxvalue", row.get("maxvalue"), pos++);
                addPropField(fields, "increment", row.get("increment"), pos++);
                addPropField(fields, "cycle", row.get("cycle"), pos);
            }
        } catch (Exception e) {
            LOG.debug("MonetDB getFieldsForSequence: {}", e.getMessage());
        }
    }

    private void getFieldsForIndex(ConnectionResource resource, String containerId, String entityId, List<FieldInfo> fields) {
        String composite = entityId.substring(MonetDBConstants.ENTITY_ID_PREFIX_INDEX.length());
        int dot = composite.indexOf('.');
        if (dot <= 0) return;
        String tablename = composite.substring(0, dot);
        String idxname = composite.substring(dot + 1);
        String sql = "SELECT it.index_type_name AS index_type FROM sys.idxs i "
                + "JOIN sys.tables t ON i.table_id = t.id JOIN sys.schemas s ON t.schema_id = s.id "
                + "LEFT JOIN sys.idx_types it ON i.type = it.index_type_id WHERE s.name = ? AND t.name = ? AND i.name = ?";
        try {
            List<Map<String, Object>> rows = resource.queryForList(sql, "getFieldsForIndex", false, containerId, tablename, idxname);
            if (rows != null && !rows.isEmpty()) {
                String typeName = Utils.getString(rows.get(0), "index_type");
                fields.add(new FieldInfo("index_type", typeName != null ? typeName : "VARCHAR").withPosition(0).withNullable(true));
            }
        } catch (Exception e) {
            LOG.debug("MonetDB getFieldsForIndex: {}", e.getMessage());
        }
    }

    private void getFieldsForTrigger(ConnectionResource resource, String containerId, String entityId, List<FieldInfo> fields) {
        String composite = entityId.substring(MonetDBConstants.ENTITY_ID_PREFIX_TRIGGER.length());
        int dot = composite.indexOf('.');
        if (dot <= 0) return;
        String tablename = composite.substring(0, dot);
        String trigname = composite.substring(dot + 1);
        String sql = "SELECT tr.\"time\", tr.orientation, tr.event, tr.condition, tr.statement FROM sys.triggers tr "
                + "JOIN sys.tables t ON tr.table_id = t.id JOIN sys.schemas s ON t.schema_id = s.id WHERE s.name = ? AND t.name = ? AND tr.name = ?";
        try {
            List<Map<String, Object>> rows = resource.queryForList(sql, "getFieldsForTrigger", false, containerId, tablename, trigname);
            if (rows != null && !rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                int pos = 0;
                addPropField(fields, "time", row.get("time"), pos++);
                addPropField(fields, "orientation", row.get("orientation"), pos++);
                addPropField(fields, "event", row.get("event"), pos++);
                addPropField(fields, "condition", row.get("condition"), pos++);
                addPropField(fields, "statement", row.get("statement"), pos);
            }
        } catch (Exception e) {
            LOG.debug("MonetDB getFieldsForTrigger: {}", e.getMessage());
        }
    }

    private static void addPropField(List<FieldInfo> fields, String name, Object value, int position) {
        String typeName = value != null ? value.getClass().getSimpleName() : "VARCHAR";
        if (value instanceof Number) typeName = "BIGINT";
        if (value instanceof Boolean) typeName = "BOOLEAN";
        fields.add(new FieldInfo(name, typeName).withPosition(position).withNullable(true));
    }
}
