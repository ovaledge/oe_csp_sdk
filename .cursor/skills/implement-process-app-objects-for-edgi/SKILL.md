---
name: implement-process-app-objects-for-edgi
description: Implement processAppObjectsForEdgi() for OvalEdge connectors using proven in-repo connector patterns. Use when a connector needs EDGI bridge object processing.
---

# Implement `processAppObjectsForEdgi()` Skill

Implements `processAppObjectsForEdgi(EdgiConnectorObjectRequest request)` in connector classes using reference patterns from existing connectors (MonetDB, AWS Console).

---

## Mandatory behavior (strict)

- **Ask before coding when required inputs are unclear**:
  - expected `fullyQualifiedObjectName` format (e.g. `container.object` vs `object`),
  - which `ObjectKind` should be queried (`ENTITY`, `VIEW`, etc.),
  - whether `containerId` is required for that connector’s `QueryService`,
  - whether EDGI should handle empty result as success with parquet audit.
- **Strictly no assumptions**:
  - do not assume object naming format,
  - do not assume query entity type,
  - do not assume path behavior beyond `EdgiBridgeUtils.getJpTempPath(connInfo)`,
  - do not assume connector-specific parsing logic unless present in code/docs.
- **Use existing connector behavior first**: align with current `fetchData()` contract in that connector.

---

## Reference baseline

This skill is based on patterns found in:

- `monetdb/.../MonetDBConnector.java`
- `awsconsole/.../AwsConsoleConnector.java`
- Similar connector implementations that write EDGI parquet through `EdgiBridgeUtils`.

Core shared flow from those implementations:
1. Build `QueryRequest` from EDGI request (`entityId`, optional `containerId`, `entityType`, `fields`, `connectionConfig`).
2. Call `queryService.fetchData(queryRequest)`.
3. Normalize null `data` to empty list.
4. Resolve JP temp path (`EdgiBridgeUtils.getJpTempPath(connInfo)`) and normalize trailing separator.
5. Write row-count parquet to `workspaceId/audit`.
6. If empty rows, return `EdgiBridgeUtils.handleEmptyEntity(...)`.
7. Else write data parquet via `EdgiBridgeUtils.handleAppObjectsDataToParquet(...)`.
8. Set `response.objectProcessed`.
9. On exception, log sanitized warning and set response failure message.

---

## Required imports

```java
import com.ovaledge.csp.dto.model.ConnInfo;
import com.ovaledge.csp.sdk.edgi.EdgiBridgeUtils;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import com.ovaledge.csp.v3.core.apps.model.request.EdgiConnectorObjectRequest;
import com.ovaledge.csp.v3.core.apps.model.request.QueryRequest;
import com.ovaledge.csp.v3.core.apps.model.response.EdgiConnectorObjectResponse;
import com.ovaledge.csp.v3.core.apps.model.response.QueryResponse;
import com.ovaledge.csp.v3.core.apps.utils.LogUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
```

---

## Recommended implementation pattern (robust)

Use this robust structure unless the connector already has a validated variant:

```java
@Override
public EdgiConnectorObjectResponse processAppObjectsForEdgi(EdgiConnectorObjectRequest request) {
    EdgiConnectorObjectResponse response = new EdgiConnectorObjectResponse();
    String fqn = request.getFullyQualifiedObjectName();
    if (fqn == null || fqn.isBlank()) {
        response.setObjectProcessed(false);
        response.setEdgiProcessingMessage("fullyQualifiedObjectName is required.");
        return response;
    }

    // Parse according to the connector's agreed format.
    String containerId = null;
    String entityId = fqn.trim();
    if (entityId.contains(".")) {
        int firstDot = entityId.indexOf('.');
        containerId = entityId.substring(0, firstDot);
        entityId = entityId.substring(firstDot + 1);
    }
    if (entityId.isBlank()) {
        response.setObjectProcessed(false);
        response.setEdgiProcessingMessage("Invalid fullyQualifiedObjectName.");
        return response;
    }

    String entityNameForParquet = entityId;
    int workspaceId = request.getWorkspaceId();
    ConnInfo connInfo = request.getConnInfo();

    try {
        QueryRequest queryRequest = new QueryRequest();
        if (containerId != null && !containerId.isBlank()) {
            queryRequest.setContainerId(containerId);
        }
        queryRequest.setEntityId(entityId);
        queryRequest.setEntityType(ObjectKind.ENTITY.value()); // confirm if different
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
            tempPath = tempPath.endsWith(File.separator) ? tempPath : tempPath + File.separator;
        }

        Long rowCount = queryResponse.getTotalRows() != null
                ? Long.valueOf(queryResponse.getTotalRows())
                : Long.valueOf(data.size());

        String countPath = tempPath + workspaceId + File.separator + "audit" + File.separator;
        EdgiBridgeUtils.writeTableRowCountToParquet(countPath, entityNameForParquet, rowCount);

        if (rowCount == 0L || data.isEmpty()) {
            return EdgiBridgeUtils.handleEmptyEntity(entityNameForParquet, workspaceId, tempPath, request);
        }

        boolean processed = EdgiBridgeUtils.handleAppObjectsDataToParquet(
                data, tempPath, workspaceId, entityNameForParquet);
        response.setObjectProcessed(processed);

    } catch (Exception e) {
        LogUtils.logSystemWarn(LOG, "processAppObjectsForEdgi failed.", e);
        response.setObjectProcessed(false);
        response.setEdgiProcessingMessage(e.getMessage());
    }
    return response;
}
```

---

## Connector-specific decisions (must confirm, never assume)

Before implementation, confirm these points:

1. **FQN format**
   - `container.object` (MonetDB-style) or `object` (AWS/Zoho-style extraction)?
2. **Query routing**
   - Is `containerId` mandatory for `fetchData()` in this connector?
3. **Entity type**
   - Always `ObjectKind.ENTITY.value()` or connector-specific kind?
4. **Parquet naming**
   - Use raw `entityId`, object suffix, or normalized name?
5. **Field projection**
   - Pass `request.getObjectFieldNames()` as-is or transform names?

If any answer is unknown, ask the user or inspect connector `QueryService` behavior.

---

## Validation checklist after implementation

- [ ] Method exists in connector class with `@Override`.
- [ ] Builds `QueryRequest` with `connectionConfig = request.getConnInfo().toConnectionConfig()`.
- [ ] Uses connector-correct `entityId` (and `containerId` if required).
- [ ] Propagates field list when present.
- [ ] Handles null data list safely.
- [ ] Writes row count parquet under `workspaceId/audit`.
- [ ] Calls `handleEmptyEntity` when no rows.
- [ ] Calls `handleAppObjectsDataToParquet` when rows exist.
- [ ] On exception: logs warning and sets failure response message.
- [ ] No secrets or credentials in logs.

---

## Testing guidance

Add/extend connector tests to cover:

1. **Invalid FQN** -> `objectProcessed=false`, clear message.
2. **Empty query result** -> returns `handleEmptyEntity` path.
3. **Non-empty query result** -> `objectProcessed=true` when parquet write succeeds.
4. **Exception path** -> failure response and sanitized logging.

Prefer mocking `queryService.fetchData()` and static parquet bridge behavior where feasible.

---

## Notes

- Keep implementation inside connector class (not metadata/query service).
- Reuse the connector’s existing parsing and naming conventions.
- If EDGI is not required for a connector, leave default behavior and document it explicitly.

## Related skills

- **build-new-connector-sdk** — optional `processAppObjectsForEdgi` mention in connector checklist
- **unit-test-generation** — mock `fetchData` and empty/non-empty parquet paths
- **connector-code-review** — only when EDGI is in scope for the connector
