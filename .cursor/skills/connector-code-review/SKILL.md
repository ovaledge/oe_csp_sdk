---
name: connector-code-review
description: Validate OvalEdge CSP SDK connector implementations against SDK requirements before PR submission. Use when user asks to review a connector, check for issues, or prepare for PR.
---

# Connector Code Review Skill for OvalEdge CSP SDK

Systematically validate connector implementations against SDK requirements, interface contracts, and quality gates before PR submission.

**Authoritative checklist:** `.docs/sdk/10.Definition_of_Done_and_Success_Criteria.md` (merge any gaps found here into that DoD).

## Mandatory behavior (strict)

- **Evidence-based review**: Cite file paths and line-level issues; do not assume undocumented behavior passes.
- **No assumptions on completeness**: If tests, `@SdkConnector`, or `configs/{connector-id}.json` were not verified, state that explicitly in the review output.
- **Severity honesty**: Mark blockers as CRITICAL only when they violate SDK contract or break discovery/runtime.

---

## When to Use

- User asks to "review" or "check" a connector implementation
- User is preparing a connector for PR submission
- User asks if a connector is "ready" or "complete"
- User mentions "Definition of Done" or "quality gates"
- User asks to validate connector against SDK requirements

---

## Review Process

When asked to review a connector, follow this systematic checklist in order:

### Phase 1: Structure Validation

**1.1 Module Structure**

Check that the connector module has the correct layout:

```
{connector-id}/
├── pom.xml
├── src/main/java/com/ovaledge/csp/apps/{package}/
│   ├── main/
│   │   ├── {Prefix}Connector.java
│   │   ├── {Prefix}MetadataService.java
│   │   └── {Prefix}QueryService.java
│   ├── constants/
│   │   └── {Prefix}Constants.java
│   ├── client/                           (optional: REST/OAuth2 only)
│   │   └── {Prefix}Client.java
│   └── quick/
│       ├── {Prefix}Controller.java
│       └── {Prefix}QuickApplication.java
├── src/main/resources/
│   ├── META-INF/services/
│   │   └── com.ovaledge.csp.v3.core.apps.service.AppsConnector
│   └── static/
│       └── {connector-id}.png
└── src/test/java/com/ovaledge/csp/apps/{package}/main/
    ├── {Prefix}ConnectorTest.java
    ├── {Prefix}MetadataServiceTest.java
    └── {Prefix}QueryServiceTest.java
```

**1.2 SPI Registration**

- [ ] File exists: `META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector`
- [ ] Contains exactly one line with the FQCN of the Connector class
- [ ] FQCN matches the actual class name and package

**1.3 Maven Integration**

- [ ] Connector added to parent `pom.xml` `<modules>` section
- [ ] Connector added to parent `pom.xml` `<dependencyManagement>` section
- [ ] Connector added as dependency in `csp-api/pom.xml`
- [ ] Connector added as dependency in `assembly/pom.xml`
- [ ] Connector `pom.xml` has correct parent reference
- [ ] Connector `pom.xml` has `spring-boot-maven-plugin` with `<mainClass>` for quick app

**1.4 Registration config**

- [ ] `src/main/resources/configs/{connector-id}.json` exists
- [ ] `connectorMaster.server` matches `getServerType()` (lowercase, stable)
- [ ] `dtoRegisterName` matches connector class FQCN
- [ ] Capability flags in JSON match what the connector actually implements (no assumed crawling/query features)

---

### Phase 2: Interface Contract Compliance

**2.1 AppsConnector Implementation**

Review `{Prefix}Connector.java`:

| Method | Check |
|--------|-------|
| `getServerType()` | Returns stable, non-empty, lowercase string; matches Constants.SERVER_TYPE |
| `getMetadataService()` | Returns non-null, reusable instance |
| `getQueryService()` | Returns non-null, reusable instance |
| `validateConnection(ConnectionConfig)` | Validates required fields; returns structured response; handles null config |
| `getAttributes()` | Includes credential manager attributes, generic attributes, connector-specific, governance, security roles |
| `exchangeAttributes(ConnInfo)` | Maps ConnInfo → attribute map; preserves values |
| `exchangeAttributes(Map)` | Maps attributes → ConnInfo; sets servertype correctly |
| `getExtendedAttributes(Map)` | Does not remove base attributes |

**Critical checks:**
- [ ] Class has `@SdkConnector(artifactId = "{connector-id}")` annotation
- [ ] Extends `BaseAppConnector` and implements `AppsConnector`
- [ ] `getServerType()` returns a constant from Constants class
- [ ] Services are instantiated once (not per-call)

**2.2 MetadataService Implementation**

Review `{Prefix}MetadataService.java`:

| Method | Check |
|--------|-------|
| `getSupportedObjects()` | Returns `ObjectKind` enum values; display names are meaningful |
| `getContainers(ContainersRequest)` | Handles null request; returns deterministic container IDs |
| `getObjects(ObjectRequest)` | Dispatches by `request.getEntityType()`; uses filters for subtypes |
| `getFields(FieldsRequest)` | Dispatches by `request.getEntityType()`; returns consistent field names |

**Critical checks:**
- [ ] `getSupportedObjects()` uses `ObjectKind.ENTITY.value()`, not hardcoded strings
- [ ] Entity types match between `getSupportedObjects()` → `getContainers()` → `getObjects()` → `getFields()`
- [ ] Object IDs are stable and resolvable by `getFields()` and `fetchData()`
- [ ] For DBMS: INDEX has `tableName` property; code objects have `source` property

**2.3 QueryService Implementation**

Review `{Prefix}QueryService.java`:

| Method | Check |
|--------|-------|
| `fetchData(QueryRequest)` | Validates required fields; respects limit/offset; returns consistent row keys |

**Critical checks:**
- [ ] Row map keys match field names from `getFields()`
- [ ] `columnNames` order matches row schema
- [ ] Pagination honors `limit` and `offset`
- [ ] `totalRows` is accurate

---

### Phase 3: Thread Safety and State

**3.1 Thread Safety**

- [ ] No mutable instance fields that hold request-specific state
- [ ] Services are stateless or use thread-safe shared state
- [ ] No request data cached in instance fields

**3.2 Connection Pool Usage**

For REST/OAuth2 connectors:
- [ ] Uses `ConnectionPoolManager.getInstance().getOrCreateResource(restConfig, ResourceType.REST)`
- [ ] Calls `removeResource(connectionInfoId)` on auth failure (401)
- [ ] Does NOT create raw HTTP clients for production traffic

For JDBC connectors:
- [ ] Uses `ConnectionPoolManager` to get JDBC resource
- [ ] Does NOT build or complete `ConnectionConfig` (uses as-is from request)

---

### Phase 4: Security and Logging

**4.1 Sensitive Data Handling**

- [ ] Password/token/secret attributes have `isMasked() == true` in `getAttributes()`
- [ ] No credentials hardcoded in source code
- [ ] No credentials in test fixtures committed to repo
- [ ] Error messages do not expose credentials, SQL, or stack traces

**4.2 Logging Hygiene**

Search for logging statements and verify:
- [ ] No credentials, tokens, passwords logged at any level
- [ ] No PII (personally identifiable information) logged
- [ ] Exceptions are logged without sensitive payload details
- [ ] Log messages are useful for debugging without exposing secrets

---

### Phase 5: Constants and Attributes

**5.1 Constants Class**

Review `{Prefix}Constants.java`:

- [ ] `SERVER_TYPE` is defined and unique
- [ ] All attribute keys have constants (e.g., `KEY_CLIENT_ID`, `KEY_BASE_URL`)
- [ ] Labels and descriptions are defined for UI display
- [ ] No hardcoded strings duplicated elsewhere

**5.2 Connection Attributes**

- [ ] All required connection parameters have attributes
- [ ] Sensitive fields are marked as `isMasked()`
- [ ] Attributes have meaningful labels and descriptions
- [ ] Default values are sensible (if any)

---

### Phase 6: Quick Package

**6.1 Quick Application**

Review `{Prefix}QuickApplication.java`:
- [ ] `@SpringBootApplication` with correct `scanBasePackages`
- [ ] Excludes `DataSourceAutoConfiguration.class`
- [ ] Has `main()` method with `SpringApplication.run()`

**6.2 Quick Controller**

Review `{Prefix}Controller.java`:
- [ ] `@RestController` and `@RequestMapping("/api/{server-type}")`
- [ ] `GET /health` endpoint returns status and connector type
- [ ] `GET /supported-objects` endpoint calls metadata service
- [ ] `POST /connection/validate` endpoint accepts `ConnectionConfig`

---

### Phase 7: Tests

**7.1 Test Coverage**

- [ ] `{Prefix}ConnectorTest.java` exists with tests for all public methods
- [ ] `{Prefix}MetadataServiceTest.java` exists with metadata tests
- [ ] `{Prefix}QueryServiceTest.java` exists with query tests
- [ ] Tests cover happy path, error scenarios, and edge cases
- [ ] Tests pass: `mvn test -pl {connector-id}`

**7.2 Edge Case Coverage**

Tests should cover:
- [ ] Null/blank required identifiers
- [ ] Unknown entityType
- [ ] Empty container/object sets
- [ ] Pagination bounds (negative offset, zero limit)

---

### Phase 8: Documentation

**8.1 Module Documentation**

- [ ] `README.md` exists in connector module
- [ ] Documents developer setup
- [ ] Lists supported object types
- [ ] Notes any limitations or unsupported features

**8.2 End-User Documentation**

- [ ] Connector documentation using the OvalEdge template
- [ ] Linked from module `README.md`

---

## Review Output Format

After reviewing, provide a summary in this format:

```
## Connector Review: {connector-name}

### Status: [READY FOR PR / NEEDS CHANGES]

### Passed Checks
- ✅ SPI registration present and correct
- ✅ Interface contracts implemented
- ✅ Thread-safe implementation
- ...

### Issues Found
1. **[CRITICAL]** {Issue description}
   - File: {path}
   - Line: {number}
   - Fix: {suggested fix}

2. **[HIGH]** {Issue description}
   - File: {path}
   - Fix: {suggested fix}

3. **[MEDIUM]** {Issue description}
   - Fix: {suggested fix}

### Recommendations
- {Optional improvements}
```

---

## Severity Levels

| Severity | Definition |
|----------|------------|
| **CRITICAL** | Blocks PR merge; violates SDK contract or causes runtime failures |
| **HIGH** | Should be fixed before PR; security issues, missing tests, logging violations |
| **MEDIUM** | Recommended fixes; code quality, documentation gaps |
| **LOW** | Nice to have; style improvements, optional enhancements |

---

## Quick Validation Commands

Run these to verify the connector before full review:

```bash
# Build the connector
mvn clean install -pl {connector-id} -am

# Run tests
mvn test -pl {connector-id}

# Check SPI file exists
cat {connector-id}/src/main/resources/META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector

# Build and run csp-api to verify discovery
mvn clean install -pl csp-api -am
java -jar csp-api/target/csp-api-*.jar
# Then: curl http://localhost:8800/v1/info | jq '.connectors[] | select(.serverType == "{server-type}")'
```

---

## Common Issues and Fixes

### 1. Connector not appearing in /v1/info
- Check SPI file path and interface name
- Verify FQCN matches actual class
- Ensure connector is in csp-api dependencies

### 2. validateConnection always fails
- Check null handling for ConnectionConfig
- Verify required attribute validation
- Check that exchangeAttributes mapping is correct

### 3. Empty metadata results
- Verify entityType consistency across methods
- Check getSupportedObjects returns correct ObjectKind values
- Ensure getContainers/getObjects use same identity keys

### 4. Query data doesn't match fields
- Verify row map keys match getFields() field names
- Check columnNames order
- Ensure fetchData uses same entityId semantics as getFields

---

## Reference Files

For a complete reference implementation, review:
- `zohodesk/` — REST/OAuth2 connector
- `monetdb/` — JDBC connector
- `awsconsole/` — AWS SDK connector

## Related skills

- **unit-test-generation** — required test coverage for public catalog PRs
- **connector-debugging** — if review finds discovery, validate, or metadata routing failures
- **build-new-connector-sdk** — implementation patterns this review enforces
