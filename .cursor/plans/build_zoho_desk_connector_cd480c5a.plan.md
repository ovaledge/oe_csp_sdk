---
name: Build Zoho Desk Connector
overview: Implement the Zoho Desk connector on top of the existing skeleton using refresh-token OAuth, core metadata/query coverage for Tickets/Contacts/Accounts/Departments/Agents, and production-ready pagination/rate-limit/error handling via ConnectionPoolManager.
todos:
  - id: constants-and-attributes
    content: Define Zoho Desk constants and connector-specific connection attributes (including secret fields).
    status: completed
  - id: connector-validation
    content: Implement ZohodeskConnector validation and symmetric attribute exchange with ConnectionPoolManager auth handling.
    status: completed
  - id: metadata-core5
    content: Implement metadata discovery for organizations containers and core 5 entity object/field discovery.
    status: completed
  - id: query-core5
    content: Implement query fetchData for core 5 entities with offset/limit mapping and totalRows behavior.
    status: completed
  - id: retry-rate-limit-errors
    content: Implement centralized retry, throttling, and error mapping for 401/429/5xx and Zoho error codes.
    status: completed
  - id: quick-endpoint
    content: Add missing quick controller connection validation endpoint.
    status: completed
  - id: tests
    content: Add zohodesk unit tests for connector, metadata, query, and retry/pagination behavior.
    status: completed
  - id: docs
    content: Create zohodesk README and clean references documentation with accurate URLs and usage notes.
    status: completed
  - id: verify-build
    content: Run module tests/build and fix any introduced lint or compile issues.
    status: completed
isProject: false
---

# Zoho Desk Connector Implementation Plan

## Scope And Targets
- Build in-module implementation for `zohodesk` with **refresh-token OAuth only**.
- Model supported objects as:
  - **ENTITY**: single consolidated object type representing modules (Tickets, Contacts, Accounts, Departments, Agents).
  - **REPORT**: report object type sourced from Zoho Desk reports endpoints.
- Keep Organizations as top-level containers and use them to validate/anchor `orgId` behavior.
- Use `ConnectionPoolManager` + REST resource for all source calls; no raw HTTP client paths.

## Files To Implement/Update
- Connector core:
  - [`/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/main/java/com/ovaledge/csp/apps/zohodesk/constants/ZohodeskConstants.java`](/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/main/java/com/ovaledge/csp/apps/zohodesk/constants/ZohodeskConstants.java)
  - [`/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/main/java/com/ovaledge/csp/apps/zohodesk/main/ZohodeskConnector.java`](/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/main/java/com/ovaledge/csp/apps/zohodesk/main/ZohodeskConnector.java)
  - [`/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/main/java/com/ovaledge/csp/apps/zohodesk/main/ZohodeskMetadataService.java`](/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/main/java/com/ovaledge/csp/apps/zohodesk/main/ZohodeskMetadataService.java)
  - [`/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/main/java/com/ovaledge/csp/apps/zohodesk/main/ZohodeskQueryService.java`](/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/main/java/com/ovaledge/csp/apps/zohodesk/main/ZohodeskQueryService.java)
- Quick app endpoint completion:
  - [`/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/main/java/com/ovaledge/csp/apps/zohodesk/quick/ZohodeskController.java`](/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/main/java/com/ovaledge/csp/apps/zohodesk/quick/ZohodeskController.java)
- Tests (new):
  - [`/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/test/java/com/ovaledge/csp/apps/zohodesk/main/ZohodeskConnectorTest.java`](/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/test/java/com/ovaledge/csp/apps/zohodesk/main/ZohodeskConnectorTest.java)
  - [`/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/test/java/com/ovaledge/csp/apps/zohodesk/main/ZohodeskMetadataServiceTest.java`](/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/test/java/com/ovaledge/csp/apps/zohodesk/main/ZohodeskMetadataServiceTest.java)
  - [`/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/test/java/com/ovaledge/csp/apps/zohodesk/main/ZohodeskQueryServiceTest.java`](/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/test/java/com/ovaledge/csp/apps/zohodesk/main/ZohodeskQueryServiceTest.java)
- Documentation:
  - [`/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/README.md`](/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/README.md)
  - [`/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/main/resources/references.md`](/Users/ovaledge/Documents/ovaledgeinc/oe_csp_sdk/zohodesk/src/main/resources/references.md)

## Implementation Design

### 1) Constants And Connection Attributes
- Define stable keys/labels/descriptions for:
  - `ZOHO_DESK_BASE_URL`, `ZOHO_ACCOUNTS_URL`, `ZOHO_ORG_ID`, `ZOHO_CLIENT_ID`, `ZOHO_CLIENT_SECRET`, `ZOHO_REFRESH_TOKEN`, optional `ZOHO_ACCESS_TOKEN`.
- Add entity subtype constants and endpoint constants for core resources.
- Add retry/rate-limit constants: max retries, backoff base/max, HTTP 429/401 handling markers.

### 2) Connector (Auth + Validation + Attribute Exchange)
- Implement `getAttributes()` with required and masked fields (secret manager-enabled for secret attributes).
- Implement `validateConnection(ConnectionConfig)` to:
  - Validate required attributes for refresh flow.
  - Build REST configuration for pooled resource usage.
  - Call lightweight endpoint (`/organizations` then scoped check with `orgId`) and return clear success/failure messages.
  - **Do not send pagination params to `/organizations`** (Zoho returns 422 for extra `from`/`limit`).
  - Evict pooled resource on auth failures (`401`/`INVALID_OAUTH`) via `removeResource(connectionInfoId)`.
- Keep exchange methods symmetric so saved connection values round-trip reliably.

### 3) Shared REST/OAuth Utility Layer (within module)
- Add a small internal helper (same module/package) used by metadata + query for:
  - Building REST request headers (`Authorization`, `orgId`).
  - Translating CSP offset/limit to Zoho `from`/`limit`.
  - Central retry policy:
    - 429 + `Retry-After` respect.
    - bounded exponential backoff for transient 5xx/threshold/concurrency errors.
    - single refresh/retry path for token-expired flows.
- Keep implementation aligned to prompt requirement: source calls through `ConnectionPoolManager` resources only.

### 4) Metadata Service
- `getSupportedObjects()` returns:
  - one **ENTITY** supported object ("Entities") for all modules,
  - one **REPORT** supported object ("Reports").
- `getContainers()` fetches organizations and maps to container `ObjectInfo` (id/path/comment), and **calls `/organizations` without pagination query params**.
- `getObjects(ObjectRequest)` behavior:
  - for **ENTITY**: return module objects (`Tickets`, `Contacts`, `Accounts`, `Departments`, `Agents`) instead of returning ticket records.
  - for **REPORT**: fetch report objects from reports API.
- `getFields(FieldsRequest)` returns dynamic field info by sampling object payload keys (or schema endpoint where available), with stable field type inference.

### 5) Query Service
- Implement `fetchData(QueryRequest)` for ENTITY modules and REPORT.
- Resolve target endpoint from entity/subtype and container context.
- For **REPORT** queries:
  - resolve report id generically (numeric `entityId`, `reportId` filter, or report name lookup from `/reports`),
  - fetch report detail by resolved id (e.g. `/reports/{id}`),
  - normalize payload into query rows (`data`/`rows`/`records`/`results` if present; otherwise detail object fallback),
  - apply requested field projection and limit/offset windowing.
- Respect requested `limit`/`offset`; convert to Zoho pagination; return:
  - `data` rows,
  - `totalRows` (from count endpoint where available, fallback to page-derived count behavior),
  - `success/message` with actionable error text.

### 6) Quick Controller Completion
- Add missing endpoint:
  - `POST /api/zohodesk/connection/validate` to call connector `validateConnection`.
- Keep existing `health` and `supported-objects` endpoints.

### 7) Tests
- Add unit tests patterned after monetdb tests for:
  - server type and required attributes,
  - connector attribute exchange round-trip,
  - supported objects integrity for consolidated ENTITY + REPORT,
  - subtype/endpoint resolution coverage including REPORT,
  - report row normalization behavior (tabular + fallback detail object),
  - pagination translation and validation guards,
  - retry/error classification utility behavior (429, 401, 5xx).
- Keep tests isolated from live Zoho API by mocking service/helper boundaries.

### 8) Docs
- Create connector README with:
  - required connection attributes,
  - supported object model (ENTITY modules + REPORT),
  - auth mode (refresh-token only in v1),
  - pagination/rate-limit/error strategy,
  - local quick-run and test commands.
- Clean up `references.md` to include valid links and remove raw cookie/curl artifacts.

### 9) Verification
- Run module tests and compile checks:
  - `mvn -pl zohodesk -am test`
  - `mvn -pl zohodesk -am package`
- Run lint diagnostics for edited files and resolve introduced issues.
- Smoke-check quick controller startup contract.

## Post-Execution Fixes Applied
- Fixed `422 UNPROCESSABLE_ENTITY` for `/organizations` in both:
  - connection validation flow,
  - containers discovery flow,
  by suppressing `from`/`limit` query params for that endpoint.
- Corrected ENTITY object discovery to return module objects rather than ticket records.
- Added REPORT query execution path by report id with normalized row output.
- Refactored REPORT execution to avoid hardcoded report names; now uses generic report-id resolution (id/filter/name lookup) for static or dynamic report objects.
- Replaced local utility-style checks/conversions with `sdk-core` `Utils` methods where equivalent methods exist.
- Live `curl` validation against `http://localhost:8800/v1/query?displayName=Reports` confirms report querying works and currently returns report metadata/detail rows for static reports.
