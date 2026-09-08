# Connector SDK Interface Reference

Scope: `oe_csp_sdk_core` connector-facing interfaces and connector lifecycle integration with `oe_csp_sdk/csp-api`.

Workflow setup, module registration, build, and run instructions are documented in [OvalEdge Connectors Software Development Kit](OvalEdge_Connectors_Software_Development_Kit.md).

### 1\. Architecture Overview: Connector SDK Contract

The Connector Software Development Kit (SDK) establishes a contract between three core components:

* **Platform/API Layer:** (`csp-api`, `AppsService`, `AppsRegistry`)  
* **Connector Implementation:** (`AppsConnector` \+ `MetadataService` \+ `QueryService` \+ optional `ProfilingService`)  
* **Shared Connector Runtime:** (`csp-sdk-core` models, base helpers, connection/pool abstractions)

**Connector Integration Model:**

The process of integrating a connector is as follows:

1. The connector class is discovered and registered based on its `serverType`.  
2. The API layer directs incoming requests to the appropriate registered connector instance.  
3. The connector routes metadata operations to the designated `MetadataService`.  
4. The connector routes query execution tasks to the `QueryService`.  
5. If `getProfilingService()` is non-null, the connector routes profiling to that `ProfilingService`.  
6. Credential handling, including attribute exchange and secret resolution, is optionally managed by credential-manager/vault helpers within `BaseAppConnector`.

**2\. Runtime Characteristics and Concurrency**

* **Long-Lived Objects:** Connector implementations are intended to be long-lived and should be treated as reusable singletons.  
* **Concurrent Execution:** API endpoints are asynchronous (`Callable`), meaning connector methods may be invoked concurrently.  
* **Thread Safety:** All connector methods **must** be safe for concurrent execution.

### 2\. Contract: SDK Guarantees and Connector Responsibilities

**SDK Guarantees (What OvalEdge SDK Provides):**

* **Stable Interfaces:** The SDK interacts with your connector exclusively through stable interfaces: `AppsConnector`, `MetadataService`, `QueryService`, and optional `ProfilingService`.  
* **Typed Models:** It supplies defined request and response models for metadata, query, and profiling operations.  
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
7. **Optional Profiling:**  
   * If `getProfilingService()` is non-null, csp-api `/profiling/*` calls that service (`getRowCount`, `profileColumn`, `sampleProfile`, `profileFile`, `profileBatch`). Default `null` means no profiling (HTTP 400).  
8. **Optional Secrets/Vault Flows (Connector-Specific):**  
   * `createUpdateSecretsManagerObjectAndConnInfo(...)`  
   * `exchangeVaultAttributes(...)`  
   * `getActualValueFromSecretsManagerForBridge(...)`  
   * `getVaultPath(...)`

Ordering Invariants (Consistency Rules)

* The set of valid `entityType` values is strictly defined by `getSupportedObjects()`.  
* `getContainers()` and `getObjects()` must consistently accept and handle the same `entityType` values defined above.  
* The object identity semantics (`entityType`, `containerId`, `entityId`) used by `getFields()` must be identical to those used by `fetchData()` and, when implemented, by `ProfilingService` request identity (`objectKind`, `containerId`, `entityId`, `fieldName`).

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
| **`default ProfilingService getProfilingService()`** | No | Returns `null` (no profiling) | Override and return a `ProfilingService` (usually extend `AbstractProfilingService`) when the connector supports ENTITY/VIEW tabular profiling and/or FILE profiling. Match JSON `profiling` / `sampleProfiling` and crawler options (`P`, `PROFILE_OPTIONS`, `PROFILE_TYPES`) to the implementation. Test via csp-api `/profiling/*`. Full contract: [§4.4](#44-profilingservice-optional-contract). Scope: [§5.6](sdk/05.What_you_will_develop.md#56-sdk-scope-and-limitations). |
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

#### 4.4 ProfilingService (optional contract)

This section is the source of truth for optional profiling: SPI methods, result mapping to legacy Oasis types, identity, kind allow-list, and platform-adapter notes for [CLB-22](https://ovaledge.atlassian.net/browse/CLB-22). Default `AppsConnector.getProfilingService()` is `null` (no profiling). Override when implemented, usually by extending `AbstractProfilingService`.

**Service exposure**

| SDK | Notes |
|-----|--------|
| `AppsConnector.getProfilingService()` | Default `null` = no profiling. Override when implemented. |
| Capability | Oasis-ready JSON: set `profiling` / `sampleProfiling` and crawler options (`P`, `PROFILE_OPTIONS`, `PROFILE_TYPES`) when the connector implements `getProfilingService()`. In this repo, MonetDB is the full DBMS reference; sample-only uses the csp-api generator SAMPLE stub. **csp-api** `/profiling/*` exercises the SPI now. OvalEdge catalog Profile **jobs** need [CLB-22](https://ovaledge.atlassian.net/browse/CLB-22); until then the OE Profile UI may appear but jobs fail at `DtoProvider`. SDK developer guide: [05.What_you_will_develop.md](sdk/05.What_you_will_develop.md) §5.6 lists Profiling as **Partial**. |

**Methods** (`ProfilingService` ↔ legacy `DbDtoInterface`)

| SDK `ProfilingService` | Legacy `DbDtoInterface` |
|------------------------|-------------------------|
| `getRowCount(ProfileRowCountRequest)` | `getRowCount` |
| `profileColumn(ProfileColumnRequest)` | `getSqlProfileResults` (+ skip/`getNonNullCount` inside) |
| `sampleProfile(SampleProfileRequest)` | `getSampleProfileResults` / `getFirstNRowsForSample` |
| `profileFile(FileProfileRequest)` | `FilesDtoInterface.profileFile` (via `ProfileResults`) |
| `getRowCountBatch(ProfileBatchRequest)` | `getRowCountBatch` |
| `profileColumnBatch(ProfileBatchRequest)` | `getSqlProfileResultsBatch` |
| `sampleProfileBatch(ProfileBatchRequest)` | per-table `getSampleProfileResults` loop |
| `profileBatch(ProfileBatchRequest)` | mode router → batch methods above / `BOTH` |
| `resetBatchExceptionStreak()` | `resetSqlProfileBatchExceptionStreak` |
| `getSkipDataTypesForProfile()` | `getSkipDataTypesForProfile()` |

**`ProfileColumnResult` ↔ `ProfileResult`**

| SDK field | Legacy field |
|-----------|--------------|
| `minValue` | `minValue` |
| `maxValue` | `maxValue` |
| `distinctCount` | `distinctCount` |
| `notNullCount` | `notNullCount` |
| `topValuesJson` | `top50DistributionInJson` |
| `emptyCount` | `emptyCount` (`-1` = N/A) |
| `zeroCount` | `zeroCount` (`-1` = N/A) |
| `minLength` / `maxLength` | `minLength` / `maxLength` |
| `totalRowCount` | `TotalRowCount` |
| `profileStatus` | `profileStatus` — use `ProfilingConstants` / `CommonObject` named constants: `PROFILE_STATUS_SAMPLE_FALLBACK` (**1**) = SQL path requests sample fallback (e.g. Hive complex types); `PROFILE_STATUS_NULL_COUNT` (**4**) = `notNullCount` already holds null-count for Oasis (in-memory/footer paths). JDBC soft-skip (MySQL / MonetDB) leaves **status 0** with `notNullCount` as true non-null so Oasis does `rowcount - notNullCount`. Successful `sampleProfile` also leaves **status 0**. |
| `message` | `message` |
| `unsupportedDataTypeForProfile` | `isUnSupportedDataTypeForProfile` |
| `columnLength` | `columnLength` |
| `patternStats` | `patternstats` |

**`ProfileBatchResponse` ↔ `DBProfileResponse`**

| SDK | Legacy |
|-----|--------|
| `rowCounts` | `rowCounts` |
| `columnResults` | `sqlProfileResults` |
| `failedObjectsWithReason` | `failedTablesWithReason` |
| `failedFieldsWithReason` | `failedColumnsWithReason` |

**Batch methods (legacy-aligned)**

| SDK batch method | Legacy | Default behavior |
|------------------|--------|------------------|
| `getRowCountBatch` | `getRowCountBatch` | Loop `getRowCount` per target |
| `profileColumnBatch` | `getSqlProfileResultsBatch` | Loop `profileColumn` per field; streak threshold |
| `sampleProfileBatch` | per-table `getSampleProfileResults` | Loop `sampleProfile` per target |
| `profileBatch` | caller picks batch API | Routes by `batchMode`; `BOTH` = row count + column per target |
| `resetBatchExceptionStreak` | `resetSqlProfileBatchExceptionStreak` | Clears streak before a new batch plan |

Connectors may override any batch method (e.g. Bridge sends one remote payload). Callers may invoke batch methods directly or use `profileBatch` with `batchMode`.

**`profileBatch` modes**

| `batchMode` | Delegates to | Legacy parity |
|-------------|--------------|---------------|
| `ROW_COUNT` | `getRowCountBatch` | `getRowCountBatch` default loop |
| `COLUMN` (default) | `profileColumnBatch` | `getSqlProfileResultsBatch` default loop |
| `SAMPLE` | `sampleProfileBatch` | Platform packs tables; adapter calls `sampleProfileBatch` |
| `BOTH` | `profileBatch` → `runCombinedBatch` | Row count then column profile **per target** in one pass |

Sample batch populates `ProfileBatchResponse.columnResults` (same map shape as SQL batch). Batch packing/chunking remains caller-owned (`TableProfilingUtils` / CLB-22 adapter), matching legacy boundaries.

**Exceptions (adapter mapping)**

| SDK | Suggested oasis mapping |
|-----|-------------------------|
| `ProfilingException` | `CustomException` (message + level) |
| `ProfilingUnsupportedException` | Handle gracefully / skip mode |
| `ProfilingBatchThresholdExceededException` | `UnhandledExceptionThresholdExceededException` |

Soft skips must **not** throw—return result flags/message.

**Kind allow-list (no `ProfilingMode`)**

Connectors route internally using `ObjectKind` on the profiling request (`objectKind` JSON field).
Profiling support is restricted to legacy-compatible kinds:

- `ENTITY` / `VIEW`: tabular profiling via `getRowCount` / `profileColumn` / `sampleProfile`.
- `FILE`: file parsing + per-column stats via `profileFile`.

For non-supported kinds (REPORT/DASHBOARD/FUNCTION/PROCEDURE/ETL/DATASET/FILEFOLDERS/etc.), connectors must throw `ProfilingUnsupportedException` (or leave the SPI unimplemented/default-unsupported).

**`ApiDtoInterface` is obsolete for profiling**

`ApiDtoInterface` sample profiling methods (`getSampleProfileResults`, `getFirstNRows`) exist in legacy code but are not mapped into `ProfilingService` in this SDK SPI.

**Identity convention (DBMS)**

| Request field | Value |
|---------------|--------|
| `objectKind` | `ENTITY` or `VIEW` |
| `containerId` | schema |
| `entityId` | table/view name |
| `fieldName` | column |

**Identity convention (Apps sample-only)**

| Request field | Value |
|---------------|--------|
| `objectKind` | `ENTITY` |
| `containerId` | container id from `getContainers()` |
| `entityId` | object/entity id from `getObjects()` |
| `fieldName` | field key from `getFields()` |

Sample-only: override `getRowCount` and `sampleProfile`; leave `profileColumn` unsupported. OvalEdge Profile jobs call `getRowCount` first and skip the object on `ProfilingUnsupportedException` or a zero count — returning 0 is treated as no data. Cap sample to one list page (≤ 500). Honor `rowCountLimit` when set. Oasis-ready JSON: `profiling`/`sampleProfiling` true + `P`/`TC`/`S`/`D` (no `A`/`VC`). In this repo, use the csp-api generator **SAMPLE** stub as the scaffold.

**Identity convention (FILE)**

| Request field | Value |
|---------------|--------|
| `objectKind` | `FILE` |
| `containerId` | folder/bucket prefix (when applicable) |
| `entityId` | file name or extRefId identity |
| `location` | path/location string passed to legacy file profilers |
| `extRefId` | alternate file reference id (when applicable) |
| `hasHeaders` | header presence flag |
| `sheetName` | sheet name (Excel-like files, when applicable) |
| `alternateExtType` | parser fallback extension |
| `dataLakeFolder` | whether `location` points to a data-lake folder |

**Reference implementation**

- DBMS: this repo `MonetDBProfilingService` (JSON flags/options are oasis-ready: `profiling`/`sampleProfiling` true + `P`/`TC`/`VC`/`A`/`S`/`Q`/`D`).
- Sample-only: csp-api generator SAMPLE-mode `{Prefix}ProfilingService` stub (override `getRowCount` and `sampleProfile`; `profileColumn` remains base unsupported).
- Generator: Auto + Sample (DBMS/JDBC) allow-list `A`/`S`/`Q`/`D`; Sample only allow-list `S`/`D`.

**Sample stats SSOT**

- `ProfileStatsCalculator` (sdk-core) is the single source of truth for sample compute + page merge.
- Date parse / pattern logic ports `DataTypeEvaluator` (`ProfilingDateTypeSupport` + `getPattern`/`validatePattern`).
- Legacy `ProfileTable` (csp-core) is a thin adapter: maps `RemoteColumn`/`ProfileResult` and delegates.
- Sample metric bag keys match `CspCoreConstants` (`notnullo`, `distincto`, `maxo`, `mino`, …).

**Framework compatibility (future connectors)**

To add profiling for another connector without platform changes:

1. Implement `ProfilingService` (usually extend `AbstractProfilingService`).
2. Override `AppsConnector.getProfilingService()` to return it.
3. Use request options already on `AbstractProfileRequest` (`advancedSampleClause`, `advancedDataQuery`, `maxColumnProfileLength`, `sampleSize`/`samplePageSize`, `topValuesLimit`, `rowCountLimit`, `batchUnhandledExceptionThreshold`, `customProperties`).
4. Soft-skip with `ProfilingConstants` message helpers; set `unsupportedDataTypeForProfile` **only** for datatype skip.
5. Prefer `ProfileStatsCalculator` + `JdbcProfilingSqlUtils` for DBMS sample/SQL shapes; dialect-specific SQL stays in the connector.
6. Emit oasis-ready JSON capability flags and crawler options (`profiling`, `sampleProfiling`, `P`/`PROFILE_OPTIONS`/`PROFILE_TYPES`) when profiling is implemented — see MonetDB as reference. Oasis Profile UI may appear before CLB-22 wires the platform adapter.

No per-connector work is required in sdk-core beyond using these extension points.

**Parity status (checked)**

| Area | Status | Notes |
|------|--------|-------|
| Sample stats SSOT (`ProfileStatsCalculator` ↔ legacy `ProfileTable`) | **Delegated identity** | `ProfileTable` maps `RemoteColumn`/`ProfileResult` and delegates compute/merge; SSAS table orchestration stays in `ProfileTable` only |
| Profiling SPI method map (`ProfilingService` ↔ `DbDtoInterface` profiling ops) | **Strong / not byte-identical** | Core ops mapped; see gaps below |
| OvalEdge E2E job path (`TableProfilingUtils` → SDK) | **Not wired** | CLB-22; `oasis_repo` still uses `DtoProvider.getDbDto()` only |
| Connector generator (Off / JDBC DBMS / Sample / non-JDBC forced Sample) | **Covered for intended modes** | Not every `ProfileType` (`MR`/`QA`/`DEEP`/`RS`) |

**Known SPI / behavior gaps vs legacy**

| Gap | Legacy | SDK today | Action owner |
|-----|--------|-----------|--------------|
| `RemoteTable` row-count overload | `getRowCount(..., RemoteTable, ...)` | No dedicated overload; use `entityId` string | CLB-22 adapter documents mapping; Bridge follow-on if needed |
| Public `getNonNullCount` | Separate `DbDtoInterface` method | Inlined in connector soft-skip / `profileColumn` paths | Connector impls; adapter must not expect a separate call |
| Limit helpers as methods | `getTopValuesLimit` / `getSampleProfileSize` / `getCharProfileLength` | Request options on `AbstractProfileRequest` | **CLB-22 must populate** from crawler / system config |
| Batch streak scope | Primarily `getSqlProfileResultsBatch` | Applied on row-count, column, and sample batch failures | Confirm acceptable with PO; document in adapter tests |
| Dialect / ACL / spatial SQL | Per DTO (e.g. MySqlDto) | Per connector (`JdbcProfilingSqlUtils` + dialect) | Connector rollout; not sdk-core |
| `getSumAvgStdFromRemoteColumn` / `getFirstNRows` | On `DbDtoInterface` | Out of `ProfilingService` | Leave on other surfaces unless product expands SPI |
| Platform routing | N/A | SPI ready; Oasis adapter missing | **CLB-22** |

**Newer framework additions (vs legacy)**

- Unified Apps + DBMS SPI via `AppsConnector.getProfilingService()`
- Explicit `sampleProfileBatch` / `BATCH_MODE_SAMPLE` (legacy looped sample per table in platform only)
- Mode router `profileBatch` (`ROW_COUNT` / `COLUMN` / `SAMPLE` / `BOTH`)
- Typed requests, typed exceptions, shared `ProfileStatsCalculator` / `JdbcProfilingSqlUtils` / `ProfilingConstants`
- Sample-only Apps path without full DBMS SQL profiling

**Action items (CLB-22 / follow-on)**

Use these as story/task breakdown. SPI contract above is the SSOT for field/method mapping.

**A. Platform routing & adapter (`oasis_repo`) — CLB-22 MVP**

1. **Resolve SDK/App connectors to `ProfilingService`**
   - When connector is Apps/SDK type, do not rely solely on `DtoProvider.getDbDto()`.
   - Resolve `AppsConnector` bean → `getProfilingService()`; if `null`, treat as no profiling (capability gate).
2. **Implement `SdkProfilingDbDtoAdapter` (or equivalent)**
   - Present a legacy-compatible call surface to `TableProfilingUtils` / job tasks **or** branch inside utils to call SDK methods directly.
   - Map requests: `ConnInfo` / `RemoteSchema` / `RemoteTable` / `RemoteColumn` / `DBProfileRequest` / `CrawlerOption` → SDK request models (`ConnectionConfig`, identity, options).
   - Map responses: `ProfileColumnResult` → `ProfileResult`; `ProfileBatchResponse` → `DBProfileResponse` (tables in this section).
   - Map exceptions: `ProfilingException` → `CustomException`; batch threshold → `UnhandledExceptionThresholdExceededException`; unsupported → skip/graceful.
3. **Wire job orchestration without duplicating it**
   - Keep Auto / Sample / Query routing, row-count limit, sample size, batch packing in `TableProfilingUtils`.
   - For sample jobs on SDK connectors: prefer packing targets into `sampleProfileBatch` / `batchMode=SAMPLE` rather than only calling `sampleProfile` one table at a time.
   - Call `resetBatchExceptionStreak()` at batch-plan boundaries (legacy streak reset parity).
4. **Populate request options from Oasis settings**
   - Always set: `sampleSize`, `samplePageSize`, `rowCountLimit`, `topValuesLimit`, `maxColumnProfileLength`, `advancedSampleClause` / `advancedDataQuery`, `batchUnhandledExceptionThreshold`, `profileType`, `jobStepId` when available.
   - Missing options here will cause silent behavioral drift vs legacy DTOs that read system properties via `getSampleProfileSize()` etc.
5. **Identity mapping**
   - DBMS: `objectKind=ENTITY|VIEW`, `containerId=schema`, `entityId=table/view`, `fieldName=column`.
   - Apps sample: follow connector convention (sample-only identity above); adapter must not assume schema/table for all Apps.
6. **Soft-skip semantics**
   - Soft datatype/length skips must return result flags/message (status 0 JDBC-style unless connector sets named status constants); must **not** throw.
   - Preserve `profileStatus` **1** / **4** semantics when connectors set them (`ProfilingConstants`).

**B. Registration & capability sync — CLB-22**

7. **On connector register/upgrade**, sync from `connector.json` / connector master:
   - `profiling`, `sampleProfiling`
   - Crawler options: `P`, `PROFILE_OPTIONS` (`TC`/`VC`), `PROFILE_TYPES` (`A`/`S`/`Q`/`D` as declared)
8. **Gate Profile UI and jobs** on declared capability + `getProfilingService() != null`.
9. **No regression** for legacy csp-lib / `DbDtoInterface` connectors — leave `DtoProvider` path unchanged for them.

**C. Acceptance / conformance — CLB-22 + QA**

10. **E2E reference paths**
    - DBMS: MonetDB through adapter + `TableProfilingUtils` (Auto + Sample + Query where declared).
    - Apps sample: generator SAMPLE stub through adapter (`getRowCount` + `sampleProfile`; unsupported column ops handled gracefully).
11. **Catalog persistence**
    - Same column-summary / profile tables as legacy; no schema rewrite.
    - Assert field mapping (especially `topValuesJson` ↔ `top50DistributionInJson`, failure maps, partial success).
12. **Optional live metric parity**
    - Same dataset: MonetDB (or reference JDBC) sample/SQL stats vs legacy MySqlDto / `ProfileTable` path via `ProfileStatsCalculator` — catch regressions beyond unit stubs.
13. **Document / decide streak behavior**
    - SDK applies unhandled streak on row-count and sample batches as well as column batch; confirm with PO vs legacy SQL-only streak; lock expected behavior in adapter tests.

**D. Bridge / NiFi — CLB-22 follow-on**

14. Bridge-compatible remote profiling for Apps behind Bridge (same SDK message shapes; override batch methods to send one remote payload).
15. Validate Bridge E2E for a supported App connector.

**E. Stats SSOT hygiene (already mostly done — do not regress)**

16. **Keep `ProfileStatsCalculator` as single source of truth** for sample compute + page merge; do not reintroduce duplicate math in connectors or a second `ProfileTable` implementation.
17. Legacy `ProfileTable` remains a mapping/orchestration adapter only (SSAS resolve, `ProfileResult` bridge).
18. New connectors: always use `ProfileStatsCalculator` for sample stats; dialect SQL stays in the connector.

**F. Generator / scaffold follow-ons (optional; not MVP blockers for CLB-22)**

19. Generator already covers: Profiling off; JDBC DBMS (`A`/`S`/`Q`/`D`); Sample-only (`S`/`D`); non-JDBC forced Sample; `getProfilingService()` wire + unit-test scaffold.
20. Explicitly out of generator allow-list today: `MR`, `QA`, `DEEP`, `RS` — add only if product requires.
21. Optional later: runtime capability enum (`FULL_DBMS` vs `SAMPLE`), Bridge batch override scaffold, richer VC/view-only Apps mode — product decision.

**G. Out of scope for CLB-22**

- Per-connector dialect SQL and rollout (CLB-82 / CLB-96 / connector epics).
- Replacing legacy `DbDtoInterface` for csp-lib connectors.
- Expanding SPI for `getSumAvgStdFromRemoteColumn` / data-tab `getFirstNRows` unless separately requested.

**Suggested dependency order**

```
CLB-18 SPI + MonetDB + generator SAMPLE stub + this section (done / in progress)
  → CLB-22 A–C (adapter + registration + E2E acceptance)
    → CLB-22 D (Bridge)
    → Per-connector rollout + optional F
```

#### 4.5 `@SdkConnector` annotation (Recommended release metadata)

Purpose: marks connector as SDK-built and supplies release artifact metadata.

`artifactId`  
The `artifactId` is **operationally required** for proper integration with release metadata, although the type system does not enforce it (defaulting to an empty string).

**Recommendation:** It is strongly recommended to always set a stable, non-empty artifact ID.

#### 5\) Request/Response Contract Notes

##### Required request fields

\- \`ObjectRequest\`: \`entityType\`, \`containerId\`  
\- \`FieldsRequest\`: \`entityType\`, \`containerId\`, \`entityId\`  
\- \`QueryRequest\`: \`entityType\`, \`containerId\`, \`entityId\`  
\- Tabular profiling requests (\`ProfileRowCountRequest\`, \`ProfileColumnRequest\`, \`SampleProfileRequest\`): \`objectKind\`, \`containerId\`, \`entityId\`; \`fieldName\` for column profile. Identity conventions: [§4.4](#44-profilingservice-optional-contract).  
\- \`FileProfileRequest\`: \`objectKind=FILE\`, \`entityId\`, plus file fields (\`location\`, \`extRefId\`, \`hasHeaders\`, \`sheetName\`, etc.) as applicable.

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
8\. Unsupported \`ObjectKind\` on profiling (REPORT/DASHBOARD/FUNCTION/PROCEDURE/ETL/DATASET/FILEFOLDERS, etc.) — throw \`ProfilingUnsupportedException\` or leave the SPI unimplemented.  
9\. \`getProfilingService()\` is \`null\` — csp-api \`/profiling/*\` returns HTTP 400; do not declare JSON \`profiling\` / \`sampleProfiling\` in that case.

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

See [Definition of Done and Success Criteria](sdk/10.Definition_of_Done_and_Success_Criteria.md#101-definition-of-done) for the full checklist (implementation, local verification, PR, and deployment).

