# Build a New Connector in oe_csp_sdk

You are building a new **Apps connector** for the OvalEdge CSP SDK. Connectors in this repo implement `AppsConnector` (from **oe_csp_sdk_core**), are discovered via **Java ServiceLoader**, and expose **metadata** (containers, objects, fields) and **query** (fetch data) capabilities.

**Reference implementations:**
- **monetdb** — JDBC/DBMS (Tables, Views, Functions, Sequences, Indexes, Triggers, Procedures); use for database connectors: filter keys/display names/entityId prefixes in Constants, getObjects/getFields dispatch by `request.getEntityType()`, and ObjectInfo properties (e.g. tableName for INDEX, source for code objects).

---

## Agent behavior

- **Ask for inputs when needed** — If connector name, server type, data source, or API/auth details are missing or unclear, ask the user before generating. Do not guess or infer.
- **Do not assume** — Do not assume API base URLs, auth flows, object kinds, or field names. Use only what the user provides or ask for the missing information.
- **Clarify before generating** — If anything is ambiguous (e.g. “Tally” could be Tally ERP vs another product), confirm with the user before creating the module.

---

## Input Parameters

- **Connector name**: `{CONNECTOR_NAME}` (e.g. "Tally", "Xero", "MonetDB")
- **Server type**: `{SERVER_TYPE}` — unique lowercase key (e.g. `tally`, `monetdb`). Used for registration and UI.
- **Data source**: Brief description of what the connector talks to (REST API, OAuth2 app, file API, etc.).

---

## Architecture (oe_csp_sdk Only)

- **Interface**: `com.ovaledge.csp.v3.core.apps.service.AppsConnector` (from **csp-sdk-core** dependency).
- **Base class**: `com.ovaledge.csp.v3.core.apps.service.BaseAppConnector` — use for common attribute handling (credentials, governance, etc.).
- **Main class**: `{ConnectorName}Connector` extends `BaseAppConnector` implements `AppsConnector`.
- **Metadata**: `{ConnectorName}MetadataService` implements `MetadataService` (containers, objects, fields).
- **Query**: `{ConnectorName}QueryService` implements `QueryService` (fetchData).
- **Discovery**: Java SPI — file `META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector` containing the FQCN of the connector class.
---

## ConnectionPoolManager (Required for source calls)

**All calls to the source system (REST API, OAuth2, etc.) must use `ConnectionPoolManager`** from **csp-sdk-core** (`com.ovaledge.csp.v3.core.connectionpool.core.ConnectionPoolManager`). Do not open raw HTTP connections or use standalone REST clients for production traffic.

- Use `ConnectionPoolManager.getInstance().getOrCreateResource(restConfig, ResourceType.REST)` to obtain a `RestResource` for HTTP calls. Build a `RestConfig` from `ConnectionConfig` (connectionId, serverType, auth type, token endpoint, client id/secret, etc.).
- Perform requests via the returned resource (e.g. `restResource.exchange(url, method, entity, responseType)`).
- On authentication failure (e.g. 401), call `ConnectionPoolManager.getInstance().removeResource(config.getConnectionInfoId())` so the pool evicts the bad resource.
- For REST/OAuth2 connectors, implement a Client that builds RestConfig, calls getOrCreateResource, and removeResource on 401; see csp-sdk-core for ConnectionPoolManager usage.

---

## Module Layout

Create a new Maven module under `oe_csp_sdk` with this structure:

```
{connector-id}/                          e.g. monetdb, tally
├── pom.xml
├── src/main/java/com/ovaledge/csp/apps/{package}/
│   ├── main/
│   │   ├── {Prefix}Connector.java
│   │   ├── {Prefix}MetadataService.java
│   │   └── {Prefix}QueryService.java
│   ├── constants/
│   │   └── {Prefix}Constants.java
│   ├── client/                           optional: API/HTTP client
│   │   └── {Prefix}Client.java
│   ├── model/                            optional: DTOs
│   │   └── ...
│   └── quick/                            required: standalone run for local testing
│       ├── {Prefix}Controller.java
│       └── {Prefix}QuickApplication.java
└── src/main/resources/
    ├── META-INF/services/
    │   └── com.ovaledge.csp.v3.core.apps.service.AppsConnector
    └── static/
        └── {connector-id}.png            icon (e.g. monetdb.png)
```

**Naming:** `{connector-id}` = artifactId, lowercase, hyphenated (e.g. `monetdb`, `tally`). `{package}` = connector-id with hyphens removed. `{Prefix}` = PascalCase (e.g. `MonetDB`, `Tally`).

---

## 1. Connector Class (`{Prefix}Connector.java`)

- Extend `BaseAppConnector`, implement `AppsConnector`.
- Return unique `getServerType()` = `{SERVER_TYPE}` (e.g. `monetdb`, `tally`).
- Implement:
  - `validateConnection(ConnectionConfig config)` — test credentials/connectivity; return `ValidateConnectionResponse` (success/valid/message). Use `config` **as-is** (do not build or complete ConnectionConfig from attributes; the caller provides it). For OAuth2, support code exchange and “auth required” with URL.
  - `getMetadataService()` — return your `{Prefix}MetadataService`.
  - `getQueryService()` — return your `{Prefix}QueryService`.
  - `getAttributes()` — build connection form: call `getCredentialManagerCommonAttributes(attributes)`, then `getGenericAttributes()`, then add connector-specific attributes (client id/secret, tokens, environment, etc.), then `getGovernanceAttributes(attributes)` and `getSecurityAndGovernanceRolesAttributes(attributes)`. Use a **Constants** class for keys and labels.
  - `exchangeAttributes(ConnInfo connInfo)` — map saved ConnInfo back to UI attribute map (include connector-specific keys from Constants).
  - `exchangeAttributes(Map<String, ConnectionAttribute> attributes)` — map UI attributes to ConnInfo (including additionalAttr for connector-specific values).
  - `getExtendedAttributes`, `getActualValueFromSecretsManagerForBridge`, `exchangeVaultAttributes`, `createUpdateSecretsManagerObjectAndConnInfo`, `getVaultPath` — delegate to `super` with `exchangeAttributes(connInfo)` where needed.
- **Optional:** If the connector is used for EDGI/bridge data push, implement `processAppObjectsForEdgi(EdgiConnectorObjectRequest request)` (build QueryRequest, call queryService.fetchData, write parquet via BridgeUtils).

---

## 2. Constants (`{Prefix}Constants.java`)

- `SERVER_TYPE` = `"{server-type}"`.
- Connection attribute **keys** (e.g. `KEY_CLIENT_ID`, `KEY_ACCESS_TOKEN`) — used in `getAttributes()` and `exchangeAttributes`.
- **Labels** and **descriptions** for UI.
- API URLs, OAuth endpoints, rate limits, etc., as needed.
- **For DBMS connectors:** filter keys for object subtypes (e.g. `FILTER_OBJECT_SUBTYPE`, `DISPLAY_NAME_TABLES`, `DISPLAY_NAME_VIEWS`), display names for each supported type, and optional **entityId prefixes** (e.g. `func:`, `seq:`, `idx:`, `trg:`) if you use them to dispatch in getFields.

---

## ObjectKind and entity types (csp-sdk-core)

- **Source:** `com.ovaledge.csp.v3.core.apps.model.ObjectKind` in **oe_csp_sdk_core** (or csp-sdk-core). Use only these enum values for `getSupportedObjects()` and in responses.
- **Domains:** DBMS (ENTITY, VIEW, FUNCTION, SEQUENCE, INDEX, TRIGGER, PROCEDURE), Reports (REPORT, DASHBOARD), FileSystem (FOLDER, FILE, BUCKET). DBMS Views are entities; use `ObjectKind.ENTITY` for Tables and `ObjectKind.VIEW` or `ObjectKind.ENTITY` per product — document which. Reserve REPORT for report-style objects.
- **Platform contract:** The platform calls `getObjects(ObjectRequest)` with `request.getEntityType()` set to the **ObjectKind** (e.g. `ENTITY`, `FUNCTION`, `INDEX`). It may pass filters (e.g. `objectSubType`, `displayName`) when multiple subtypes map to the same kind. It calls `getFields(FieldsRequest)` with `request.getEntityType()` and `request.getEntityId()`; for INDEX it expects a list of column names as fields. The connector must **dispatch by entity type** in both getObjects and getFields so the platform can sync correctly (RemoteTable, RemoteIndex, QueryOrCode, RemoteColumn/RemoteField). Sync logic lives in the platform repo (e.g. DbCrawlingUtilsV2); this prompt only defines the connector contract.

---

## 3. MetadataService (`{Prefix}MetadataService.java`)

Implements `MetadataService` from csp-sdk-core:

- **getSupportedObjects()** — return `SupportedObjectsResponse` with a list of `SupportedObject`. **The first parameter of each `SupportedObject` must be from `ObjectKind`**: use `ObjectKind.ENTITY.value()`, `ObjectKind.REPORT.value()`, etc. (e.g. `new SupportedObject(ObjectKind.ENTITY.value(), "Entities", "Database entities")`). Map each object type the connector supports (Tables, Views, Reports, etc.) to the appropriate `ObjectKind`, with the second parameter as display name and the third as description. Include every kind the source supports; do not use a single generic type unless the source supports only one kind. **DBMS/database connectors:** database Views are entities, not reports. Use `ObjectKind.ENTITY` for both Tables and Views (e.g. display names "Tables" and "Views"). Do not use `ObjectKind.REPORT` for database views. Reserve `ObjectKind.REPORT` for report-style objects (e.g. API reports). When multiple object types map to the same ObjectKind (e.g. Tables and Views both ENTITY), use `ObjectRequest.getFilters()` in `getObjects()` to distinguish which type to return (e.g. filter key `objectSubType` or `displayName` with value `"Tables"` or `"Views"`). **Identifying object types for DBMS connectors:** Research the source’s **official documentation** (e.g. SQL catalog, system tables) to identify all supported object types (Tables, Views, Indexes, Procedures, Triggers, Functions, Sequences, etc.).
- **getContainers(ContainersRequest)** — return top-level “containers” (e.g. company, realm, workspace, schemas). Return `ObjectInfo` with id, path, comment.
- **getObjects(ObjectRequest)** — given container and entity type, return list of objects. **Dispatch by `request.getEntityType()`** (the ObjectKind): call a dedicated method per type (e.g. fetchTables, fetchViews, fetchFunctions, fetchIndexes). When multiple subtypes share one kind (e.g. Tables and Views both ENTITY), use **`request.getFilters()`** to decide which to return (e.g. filter key `objectSubType` or `displayName` with value matching your Constants). Return `ObjectInfo` with id, path, comment; set **properties** when the platform needs them for sync (see below).
- **getFields(FieldsRequest)** — given entity type and entity id, return list of `FieldInfo`. **Dispatch by `request.getEntityType()`** (or by entityId prefix if you use one). For **ENTITY/VIEW**: return column metadata (name, type, position, nullable, comment). For **INDEX**: return one `FieldInfo` per column in the index (column name and order); the platform uses these to build index definitions. For **FUNCTION/PROCEDURE/TRIGGER/SEQUENCE**: return parameters or a single “definition” field as appropriate; code objects are synced via `QueryOrCode` and do not require full column lists.
- **ObjectInfo properties for platform sync:** The platform maps your objects to DTOs (RemoteTable, RemoteIndex, QueryOrCode). Ensure:
  - **INDEX:** include a property for the **table name** (e.g. `"tableName"` or key from Constants) so the platform can associate the index with the correct table.
  - **Code objects (FUNCTION, PROCEDURE, TRIGGER):** include **source/definition** and optionally **comment** so the platform can store them in QueryOrCode.
  - **ENTITY/VIEW:** path and id are enough; optional comment in ObjectInfo.

Use `ConnectionConfig` from the request **as-is** (do not build or transform it; the caller supplies a complete config). Obtain the connection resource (e.g. JDBC from ConnectionPoolManager or REST resource) **directly** in the service and run the required operations (queries, API calls) there. Do not introduce a Client layer with wrapper methods unless the source is REST/OAuth2 (see §5).

---

## 4. QueryService (`{Prefix}QueryService.java`)

Implements `QueryService`:

- **fetchData(QueryRequest request)** — return `QueryResponse` with:
  - `data`: `List<Map<String, Object>>` (rows).
  - `totalRows`: count.
  - `success`: true/false.
  - Honor `request.getLimit()` and `request.getOffset()`; paginate if the API supports it.

Use `request.getConnectionConfig()` **as-is** and entity type/id from the request. Obtain the connection resource directly in the service and run the query there. Do not delegate to a Client unless the source is REST/OAuth2 (see §5).

---

## 5. Client — Only When Needed (REST/OAuth2)

**Do not add a Client for every connector.** Only add a `client` package and `{Prefix}Client` when:

- The source is **REST/OAuth2** (or similar) and you need a dedicated layer for building `RestConfig`, token refresh, and HTTP calls; or
- The source requires complex, reusable API/auth logic that would otherwise duplicate across MetadataService and QueryService.

**Do not add a Client for JDBC/database connectors.** For JDBC, use `ConnectionConfig` as-is and obtain the JDBC resource (e.g. `ConnectionPoolManager.getInstance().getOrCreateResource(config, ResourceType.JDBC)`, then cast to `JdbcResourceWrapper` and use `getJdbcTemplate()`) **directly** in MetadataService and QueryService. Do not create `buildConnectionConfig` or other helpers that transform or complete ConnectionConfig — the caller provides ConnectionConfig already populated.

**Do not add a Client that only wraps simple operations** (e.g. getSchemaNames, getTableNames). Use the connection directly in the services and run the SQL/API calls there.

When a Client *is* used (REST/OAuth2):

- Use ConnectionPoolManager for all source calls; obtain `RestResource` via `getOrCreateResource(restConfig, ResourceType.REST)`.
- Build `RestConfig` from `ConnectionConfig` (connectionId, serverType, auth type, token endpoint, client id/secret, etc.). Do **not** build or complete `ConnectionConfig` from attributes inside the connector — the caller provides ConnectionConfig.
- Call `removeResource(connectionInfoId)` on auth failure (e.g. 401).
- Use ConnectionPoolManager and RestConfig as documented in csp-sdk-core.

---

## 6. SPI Registration

- File: `src/main/resources/META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector`
- Content: single line = full class name of the connector, e.g. `com.ovaledge.csp.apps.monetdb.main.MonetDBConnector`

---

## 7. Maven Integration

- **Parent `pom.xml` (oe_csp_sdk):**
  - Add `<module>{connector-id}</module>` under `<modules>`.
  - Under `<dependencyManagement><dependencies>`, add:
    - `<dependency><groupId>com.ovaledge</groupId><artifactId>{connector-id}</artifactId><version>${project.version}</version></dependency>`
- **csp-api/pom.xml:** Add dependency:
  - `<dependency><groupId>com.ovaledge</groupId><artifactId>{connector-id}</artifactId></dependency>`
  - So the API server’s ServiceLoader discovers the connector.
- **assembly/pom.xml:** Add the same dependency so the connector is included in the assembly bundle.
- **Connector module pom.xml:** Parent `oe-csp-sdk`, artifactId `{connector-id}`, dependencies: `csp-sdk-core`, `csp-sdk-core`, plus any (e.g. `spring-boot-starter-web`, `org.json`, `httpclient5`, logging). No need to duplicate versions if in parent dependencyManagement.

---

## 8. Assets

- **Icon:** `src/main/resources/static/{connector-id}.png` (or `{server-type}.png`). Used in UI.

---

## 9. Quick package (required): Standalone app for local testing

**Every connector must include a `quick` package** so the connector can be run and tested standalone (without csp-api).

- **Package:** `com.ovaledge.csp.apps.{package}.quick`
- **{Prefix}QuickApplication.java** — Spring Boot entry point:
  - `@SpringBootApplication(scanBasePackages = "com.ovaledge.csp.apps.{package}", exclude = { DataSourceAutoConfiguration.class })`
  - `main(String[] args)` runs `SpringApplication.run({Prefix}QuickApplication.class, args)`.
- **{Prefix}Controller.java** — REST controller for the standalone server:
  - `@RestController`, `@RequestMapping("/api/{server-type}")` (e.g. `/api/monetdb`). Use the same path as `SERVER_TYPE` so the base path is `/api/{connector-id}`.
  - **Endpoints to implement:**
    - `GET /health` — return `{ "status": "UP", "connectorType": connector.getServerType() }`.
    - `GET /supported-objects` — return `connector.getMetadataService().getSupportedObjects()`.
    - `POST /connection/validate` — accept `@RequestBody ConnectionConfig`, call `connector.validateConnection(config)`, return `ValidateConnectionResponse`.
  - Hold a single instance: `private final {Prefix}Connector connector = new {Prefix}Connector();`
- **Connector module pom.xml:** Add `spring-boot-starter-web` (with same exclusions as `spring-boot-starter` if present). Add `spring-boot-maven-plugin` with `<mainClass>` set to the quick application class so the module can be run with `mvn spring-boot:run -pl {connector-id}`.
- **Assembly:** The assembly JAR excludes `**/quick/**` so the quick app is not bundled in the library JAR; it is only for running the connector standalone during development.

Reference: **monetdb** module (`MonetDBController`, `MonetDBQuickApplication`).

---

## Checklist Before Done

- [ ] **All source system calls use ConnectionPoolManager** (getOrCreateResource for RestResource; removeResource on auth failure). No raw HTTP clients for production calls.
- [ ] New module builds with `mvn clean install -pl {connector-id}` (and with `-am` from root).
- [ ] **Quick package** present: `quick/{Prefix}Controller.java` (health, supported-objects, connection/validate) and `quick/{Prefix}QuickApplication.java`; connector pom has `spring-boot-starter-web` and `spring-boot-maven-plugin` with mainClass.
- [ ] SPI file present and FQCN correct.
- [ ] Connector registered in parent, csp-api, and assembly poms.
- [ ] `getServerType()` is unique and matches how the UI/config will reference the connector.
- [ ] `validateConnection` returns meaningful success/failure and, for OAuth2, “auth required” with URL when needed.
- [ ] Metadata: getSupportedObjects lists the object types the source supports (e.g. Tables, Views, Procedures, Indexes); getContainers, getObjects, getFields return consistent paths.
- [ ] **getObjects** dispatches by `request.getEntityType()` and uses `request.getFilters()` when multiple subtypes share one ObjectKind (e.g. Tables vs Views).
- [ ] **getFields** dispatches by `request.getEntityType()` (or entityId prefix); for INDEX returns column names as FieldInfo; for ENTITY/VIEW returns full column metadata.
- [ ] **ObjectInfo** for INDEX includes table name in properties; code objects include source/definition (and optional comment) for platform sync.
- [ ] Query: fetchData respects limit/offset and returns correct totalRows/success.
- [ ] Constants used for all attribute keys and labels; no hardcoded connection keys in multiple places.
- [ ] If EDGI is required: `processAppObjectsForEdgi` implemented and uses BridgeUtils for parquet write.

---

## Usage

Replace `{CONNECTOR_NAME}`, `{SERVER_TYPE}`, and connector-id/package/prefix with the actual values. Use the **monetdb** module as the reference for JDBC/DBMS connectors; keep this prompt scoped to oe_csp_sdk only (no csp-lib/DbDto/DB crawl logic).
