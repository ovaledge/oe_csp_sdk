# Connector SDK Interface Reference

Scope: `oe_csp_sdk_core` connector-facing interfaces and connector lifecycle integration with `oe_csp_sdk/csp-api`.

Workflow setup, module registration, build, and run instructions are documented in [OvalEdge Connectors Software Development Kit](OvalEdge_Connectors_Software_Development_Kit.md).

### 1\. Architecture Overview: Connector SDK Contract

The Connector Software Development Kit (SDK) establishes a contract between three core components:

* **Platform/API Layer:** (`csp-api`, `AppsService`, `AppsRegistry`)  
* **Connector Implementation:** (`AppsConnector` \+ `MetadataService` \+ `QueryService`)  
* **Shared Connector Runtime:** (`csp-sdk-core` models, base helpers, connection/pool abstractions)

**Connector Integration Model:**

The process of integrating a connector is as follows:

1. The connector class is discovered and registered based on its `serverType`.  
2. The API layer directs incoming requests to the appropriate registered connector instance.  
3. The connector routes metadata operations to the designated `MetadataService`.  
4. The connector routes query execution tasks to the `QueryService`.  
5. Credential handling, including attribute exchange and secret resolution, is optionally managed by credential-manager/vault helpers within `BaseAppConnector`.

**2\. Runtime Characteristics and Concurrency**

* **Long-Lived Objects:** Connector implementations are intended to be long-lived and should be treated as reusable singletons.  
* **Concurrent Execution:** API endpoints are asynchronous (`Callable`), meaning connector methods may be invoked concurrently.  
* **Thread Safety:** All connector methods **must** be safe for concurrent execution.

### 2\. Contract: SDK Guarantees and Connector Responsibilities

**SDK Guarantees (What OvalEdge SDK Provides):**

* **Stable Interfaces:** The SDK interacts with your connector exclusively through stable interfaces: `AppsConnector`, `MetadataService`, and `QueryService`.  
* **Typed Models:** It supplies defined request and response models for both metadata and query operations.  
* **Response Integrity:** The `BaseResponse` envelope (`success`, `message`, `errorCode`, `timestamp`) is maintained consistently across all response types.  
* **Helper Utilities:** `BaseAppConnector` includes utility methods for managing common attributes and flows related to the credential manager/vault.

**Connector Responsibilities (What Your Connector Must Do):**

* **Unique Identifier:** Furnish a **stable and unique** `serverType`.  
* **Complete Implementation:** Implement all required interface methods with predictable (deterministic) behavior.  
* **Input Validation:** Validate necessary request fields and return clear, explicit failure responses upon invalid input.  
* **Security:** **Crucially, avoid leaking sensitive data** (credentials/secrets) in exceptions, messages, or logs.  
* **Concurrency:** Ensure thread-safe operation by avoiding shared, mutable state that is specific to a request.  
* **Compatibility:** Maintain backward compatibility for `exchangeAttributes` mappings to support existing connection configurations.

### 3\. Connector Lifecycle and Execution Order

The following is the standard execution sequence for an OvalEdge Connector in a production environment:

1. **Registration and Discovery:**  
   * The connector class is loaded.  
   * The platform indexes the connector using the value returned by `getServerType()`.  
   * Release metadata is extracted from the `@SdkConnector(artifactId = "...")` annotation.  
2. **Connection Form Generation:**  
   * The platform initiates the UI generation by calling `getAttributes()`.  
   * For dynamic UI updates, `getExtendedAttributes(attributes)` may be called.  
3. **Configuration Serialization/Deserialization:**  
   * **From UI/API Payload to Configuration Object (`ConnInfo`):** The input attribute map (UI/API payload) is converted via `exchangeAttributes(Map<String, ConnectionAttribute>)` into the persisted `ConnInfo` object.  
   * **From Configuration Object to Attribute Map:** The persisted `ConnInfo` object is converted via `exchangeAttributes(ConnInfo)` back into an attribute map.  
4. **Validation:**  
   * `validateConnection(ConnectionConfig)` is called by the platform to verify the connection configuration before any metadata or query operations are executed.  
5. **Metadata Exploration:**  
   * `getMetadataService().getSupportedObjects()`  
   * `getMetadataService().getContainers(...)`  
   * `getMetadataService().getObjects(...)`  
   * `getMetadataService().getFields(...)`  
6. **Data Retrieval:**  
   * Data is fetched using `getQueryService().fetchData(QueryRequest)`.  
7. **Optional Secrets/Vault Flows (Connector-Specific):**  
   * `createUpdateSecretsManagerObjectAndConnInfo(...)`  
   * `exchangeVaultAttributes(...)`  
   * `getActualValueFromSecretsManagerForBridge(...)`  
   * `getVaultPath(...)`

Ordering Invariants (Consistency Rules)

* The set of valid `entityType` values is strictly defined by `getSupportedObjects()`.  
* `getContainers()` and `getObjects()` must consistently accept and handle the same `entityType` values defined above.  
* The object identity semantics (`entityType`, `containerId`, `entityId`) used by `getFields()` must be identical to those used by `fetchData()`.

### 4\. Interface Reference

#### 4.1 AppsConnector (Required Root Contract)

**Purpose:** This is the top-level boundary for connectors, used by the platform's orchestration layer.Required Methods

| Method | Required | Input | Output | Purpose/Invariant/Failure Mode |
| ----- | ----- | ----- | ----- | ----- |
| **`String getServerType()`** | Yes | None | Stable, non-empty server type key | **Invariant:** Value must remain constant across releases for the same connector family. **Failure Mode:** Returning `null` or a blank value will cause registry and routing failures. |
| **`ValidateConnectionResponse validateConnection(ConnectionConfig config) throws Exception`** | Yes | `ConnectionConfig (includes connector config and server type)` | `ValidateConnectionResponse with success/valid status and a message/errorCode` | **Error Contract:** May throw `Exception`, but a structured failure response is preferred for recoverable errors. **Invariant:** Must not mutate global connector state. |
| **`MetadataService getMetadataService()`** | Yes | None | Non-null MetadataService implementation | **Invariant:** Should return the same stateless and reusable service instance. |
| **`QueryService getQueryService()`** | Yes | None | Non-null QueryService implementation | **Invariant:** Should return the same stateless and reusable service instance. |
| **`Map<String, ConnectionAttribute> getAttributes()`** | Yes | None | Non-null map of attributes | Used by the connection UI and persistence mapping. **Invariant:** Keys must be stable and backward-compatible. |
| **`Map<String, ConnectionAttribute> exchangeAttributes(ConnInfo connInfo)`** | Yes | `ConnInfo` | Attribute map | **Purpose:** Converts the persisted `ConnInfo` into UI/API attributes. **Invariant:** Must preserve existing values without destructive normalization. |
| **`ConnInfo exchangeAttributes(Map<String, ConnectionAttribute> attributes)`** | Yes | Attribute map | `ConnInfo` | **Purpose:** Converts UI/API attributes to the persisted/runtime `ConnInfo`. **Invariant:** Must consistently set the connector identity (`serverType`, and connection type fields if used). |
| **`Map<String, ConnectionAttribute> getExtendedAttributes(Map<String, ConnectionAttribute> attributes)`** | Yes | Attribute map | Attribute map | **Purpose:** Provides dynamic or derived attributes (e.g., conditional fields, options, defaults). **Invariant:** Must never unexpectedly remove required base attributes. |
| **`ConnInfo getActualValueFromSecretsManagerForBridge(JSONObject object, ConnInfo connInfo)`** | Yes (Contract) | `JSONObject, ConnInfo` | `ConnInfo` | **Purpose:** Resolves external secret keys into their actual values. **Note:** Feature usage is deployment-dependent. **Invariant:** Must only resolve designated secret fields and avoid touching unrelated attributes. |
| **`ConnInfo exchangeVaultAttributes(ConnInfo connInfo, JSONObject data, String path)`** | Yes (Contract) | `ConnInfo, JSONObject, String` | `ConnInfo` | **Purpose:** Applies vault payload values to the runtime `ConnInfo`. **Note:** Feature usage is deployment-dependent. **Invariant:** Must use deterministic replacement logic across different credential managers. |
| **`SecretsManagerVo createUpdateSecretsManagerObjectAndConnInfo(String secretName, ConnInfo connInfo)`** | Yes (Contract) | `String, ConnInfo` | `SecretsManagerVo` | **Purpose:** Generates/updates the secret payload and rewrites the `ConnInfo` references. **Note:** Feature usage is deployment-dependent. **Invariant:** Must never write plaintext secrets to logs. |
| **`String getVaultPath(ConnInfo connInfo)`** | Yes (Contract) | `ConnInfo` | Vault path or empty string | **Note:** Feature usage is deployment-dependent. **Invariant:** Must not make filesystem or path traversal assumptions based on user-provided values. |

Optional/Default Methods

| Method | Required | Default Behavior | Recommendation |
| ----- | ----- | ----- | ----- |
| **`default EdgiConnectorObjectResponse processAppObjectsForEdgi(...)`** | No | Returns `null` | Only override if EDGI workflow is explicitly supported; otherwise, document the feature as unsupported. |
| **`default String getSdkVersion()`** | No | Returns the compile-time `SdkVersion.SDK_VERSION` | Do not override unless a strict compatibility strategy requires custom reporting. |

#### 4.2 \`MetadataService\` (Required discovery contract)

 The MetadataService is the required discovery contract responsible for providing metadata about browsable connector objects.

| Method | Purpose | Requirements | Input Parameters | Output/Return Value | Invariant/Constraint |
| ----- | ----- | ----- | ----- | ----- | ----- |
| **`getSupportedObjects()`** | Returns a list of supported object categories. | Required | None | `SupportedObjectsResponse` (list of `SupportedObject.typeName` etc.) | The returned `typeName` values must drive all subsequent metadata and query calls. |
| **`getContainers(ContainersRequest request)`** | Retrieves a list of containers. | Required | `connectionConfig`, optional `entityType` | `ContainersResponse` (list of `ObjectInfo` entries) | The same request context must always yield deterministic container identity keys. |
| **`getObjects(ObjectRequest request)`** | Gets the list of child objects under a specific container. | Required | Required `entityType`, required `containerId`, plus connection configuration. | `ObjectResponse` (object children list) | Object IDs must be stable and resolvable by the `getFields` and `fetchData` methods. |
| **`getFields(FieldsRequest request)`** | Fetches the field-level metadata for an object. | Required | Required `entityType`, `containerId`, `entityId`, optional options. | `FieldsResponse` (list of `FieldInfo` field metadata) | Field naming and types must remain stable to ensure query compatibility. |

#### 4.3 \`QueryService\` (Required data contract)

Purpose: metadata discovery API for browsable connector objects.

`QueryResponse fetchData(QueryRequest request) throws Exception`**Purpose:** Execute data retrieval for selected connector objects.

| Aspect | Details |
| ----- | ----- |
| **Required** | Yes |
| **Input** | **Required:** `connectionConfig`, `entityType`, `containerId`, `entityId`; **Optional:** `fields`, `filters`, `options`, `limit`, `offset` |
| **Output** | Tabular payload: `List<Map<String,Object>> data`, `columnNames`, `totalRows` |
| **Error Contract** | May throw `Exception`. For business errors, prefer `withSuccess(false)` with a meaningful `message/errorCode`. |
| **Invariants** | \- Row maps use stable field keys consistent with `getFields`. \- `columnNames` order reflects returned row schema. \- Pagination semantics (`limit/offset`) are consistent and documented. |

#### 4.4 \`@SdkConnector\` annotation (Recommended release metadata)

Purpose: marks connector as SDK-built and supplies release artifact metadata.

`artifactId`  
The `artifactId` is **operationally required** for proper integration with release metadata, although the type system does not enforce it (defaulting to an empty string).

**Recommendation:** It is strongly recommended to always set a stable, non-empty artifact ID.

#### 5\) Request/Response Contract Notes

##### Required request fields

\- \`ObjectRequest\`: \`entityType\`, \`containerId\`  
\- \`FieldsRequest\`: \`entityType\`, \`containerId\`, \`entityId\`  
\- \`QueryRequest\`: \`entityType\`, \`containerId\`, \`entityId\`

\`connectionConfig\` is required for all runtime operations that call external systems.

##### Response envelope expectations

All responses extend \`BaseResponse\`:

\- \`success\`: explicit operation status  
\- \`message\`: human-readable summary (safe for client display)  
\- \`errorCode\`: stable, machine-readable connector code (recommended)  
\- \`timestamp\`: set by base model

##### Error handling guidance

\- Prefer sanitized \`message\` values; do not expose stack traces, SQL, or secret material.  
\- Use stable connector-specific \`errorCode\` values.  
\- Throw exceptions for unrecoverable execution faults; use failure responses for expected validation/business failures.

### 6\) Thread-Safety and Performance Expectations

##### Thread-safety assumptions

\- Connector and service instances may be reused concurrently.  
\- Implementations must be stateless or use thread-safe shared state.  
\- Never cache per-request mutable objects in instance fields.

##### Performance expectations

\- \`validateConnection\`: fast-fail (timeouts and auth checks bounded).  
\- Metadata methods: avoid full scans when lightweight listing APIs exist.  
\- \`fetchData\`: respect \`limit/offset\`; avoid loading unbounded result sets into memory.  
\- Reuse pooled clients/connections where available; avoid creating heavy clients per call.

##### Minimum production controls

\- Request timeouts for upstream APIs/databases.  
\- Retries only for transient/idempotent operations.  
\- Correlation IDs in logs without secret payloads.

### 7\) Edge Cases Checklist (Must Handle)

1\. Null/blank required identifiers (\`entityType\`, \`containerId\`, \`entityId\`).  
2\. Unknown \`entityType\` passed to metadata/query paths.  
3\. Empty container/object sets (return success with empty lists where valid).  
4\. Partial field projections (requested fields missing or unsupported).  
5\. Pagination bounds (\`limit \<= 0\`, negative \`offset\`, oversized page request).  
6\. Credential manager selected but vault path/secrets missing.  
7\. Connector service unavailable or auth token expired during metadata/query.

### 8\) Implementation Blueprint (Concise)

#### 8.1 Connector class skeleton

\`\`\`java  
@SdkConnector(artifactId \= "myconnector")  
public class MyConnector extends BaseAppConnector implements AppsConnector {

   private final MetadataService metadataService \= new MyMetadataService();  
   private final QueryService queryService \= new MyQueryService();

   @Override public String getServerType() { return "my-server-type"; }  
   @Override public MetadataService getMetadataService() { return metadataService; }  
   @Override public QueryService getQueryService() { return queryService; }

   @Override  
   public ValidateConnectionResponse validateConnection(ConnectionConfig config) {  
       // perform bounded auth/connectivity check  
       return new ValidateConnectionResponse().withSuccess(true).withMessage("OK");  
   }

   @Override  
   public Map\<String, ConnectionAttribute\> getAttributes() {  
       Map\<String, ConnectionAttribute\> attrs \= new LinkedHashMap\<\>();  
       getCredentialManagerCommonAttributes(attrs);  
       attrs.putAll(getGenericAttributes());  
       // add connector-specific attrs  
       getGovernanceAttributes(attrs);  
       getSecurityAndGovernanceRolesAttributes(attrs);  
       return attrs;  
   }

   @Override  
   public Map\<String, ConnectionAttribute\> exchangeAttributes(ConnInfo connInfo) {  
       return super.exchangeAttributes(connInfo, getAttributes());  
   }  
}  
\`\`\`

### 8.2 Query service skeleton

\`\`\`java  
public class MyQueryService implements QueryService {  
   @Override  
   public QueryResponse fetchData(QueryRequest request) throws Exception {  
       // validate required identifiers and pagination  
       // execute bounded query/API call  
       // map rows \-\> List\<Map\<String,Object\>\>  
       return new QueryResponse()  
               .withSuccess(true)  
               .withData(List.of())  
               .withColumnNames(List.of())  
               .withTotalRows(0);  
   }  
}  
\`\`\`

### 9\) Definition of Done for New Connectors

A connector is production-ready when all are true:

\- Implements all required methods in \`AppsConnector\`, \`MetadataService\`, \`QueryService\`.  
\- Returns consistent object identity across metadata and query flows.  
\- Preserves backward-compatible attribute keys and mapping behavior.  
\- Handles edge cases and sanitizes all client-visible error messages.  
\- Demonstrates thread-safe behavior under concurrent requests.  
\- Documents connector-specific limits and unsupported features explicitly.

