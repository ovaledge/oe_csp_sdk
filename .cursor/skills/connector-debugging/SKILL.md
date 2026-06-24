---
name: connector-debugging
description: Systematically diagnose and fix common OvalEdge CSP SDK connector issues. Use when user reports errors, failures, or unexpected behavior in their connector.
---

# Connector Debugging Skill for OvalEdge CSP SDK

Systematically diagnose and resolve connector issues by mapping symptoms to causes and providing targeted fixes.

## Mandatory behavior (strict)

- **Diagnose before changing code**: Confirm symptom category (build, discovery, validate, metadata, query, tests) with the checklist commands below.
- **No assumed root cause**: Verify SPI, pom wiring, and `serverType` before rewriting connector logic.
- **Safe logging**: Do not add debug logs that print credentials, tokens, or full connection payloads.

---

## When to Use

- User reports a connector error or failure
- User says connector "doesn't work" or "isn't showing up"
- User has build failures, validation errors, or metadata issues
- User mentions specific error messages or stack traces
- User asks "why is my connector..." or "how do I fix..."

---

## Debugging Workflow

1. **Identify the symptom** — What error/behavior is the user seeing?
2. **Categorize the issue** — Which phase is failing?
3. **Check common causes** — Use the diagnostic checklist
4. **Apply fix** — Provide specific fix steps
5. **Validate** — Confirm the fix works

---

## Issue Categories

### Category 1: Build Failures

#### Symptom: Maven build fails with dependency errors

**Possible causes:**
- Missing `csp-sdk-core` in local Maven repo
- JFrog credentials not configured
- Wrong Java version

**Diagnostic checklist:**
```bash
# Check Java version
java -version   # Must be JDK 21
mvn -v          # Verify Maven uses JDK 21

# Check if csp-sdk-core is available
mvn dependency:tree -pl {connector-id} | grep csp-sdk-core

# Check Maven settings
mvn help:effective-settings | grep -A5 "<server>"
```

**Fix steps:**
1. Set `JAVA_HOME` to JDK 21
2. Build from repo root: `mvn clean install -DskipTests`
3. If JFrog credentials missing, add `<server>` to `~/.m2/settings.xml`

---

#### Symptom: Connector module not recognized by Maven

**Possible causes:**
- Module not added to parent `pom.xml`
- Incorrect parent reference in connector `pom.xml`

**Diagnostic checklist:**
```bash
# Check parent pom.xml modules
grep -A20 "<modules>" pom.xml | grep {connector-id}

# Check connector pom.xml parent
grep -A5 "<parent>" {connector-id}/pom.xml
```

**Fix steps:**
1. Add `<module>{connector-id}</module>` to root `pom.xml`
2. Ensure connector `pom.xml` has correct parent:
```xml
<parent>
    <groupId>com.ovaledge</groupId>
    <artifactId>oe-csp-sdk</artifactId>
    <version>${project.version}</version>
</parent>
```

---

### Category 2: Discovery Failures

#### Symptom: Connector not in `/v1/info`

**Severity:** Critical

**Possible causes:**
1. SPI file missing or incorrect
2. Connector not wired into `csp-api/pom.xml`
3. Build not refreshed after changes

**Diagnostic checklist:**
```bash
# Check SPI file exists
ls -la {connector-id}/src/main/resources/META-INF/services/

# Check SPI file contents
cat {connector-id}/src/main/resources/META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector

# Verify the FQCN class exists
ls {connector-id}/src/main/java/com/ovaledge/csp/apps/{package}/main/{Prefix}Connector.java

# Check csp-api dependency
grep "{connector-id}" csp-api/pom.xml
```

**Fix steps:**

1. Create SPI file if missing:
```
{connector-id}/src/main/resources/META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector
```

2. Add single line with FQCN:
```
com.ovaledge.csp.apps.{package}.main.{Prefix}Connector
```

3. Add to `csp-api/pom.xml`:
```xml
<dependency>
    <groupId>com.ovaledge</groupId>
    <artifactId>{connector-id}</artifactId>
</dependency>
```

4. Rebuild:
```bash
mvn clean install -pl csp-api -am
java -jar csp-api/target/csp-api-*.jar
```

---

#### Symptom: `getServerType()` returns null or blank

**Severity:** Critical

**Possible causes:**
- Constants.SERVER_TYPE not defined
- `getServerType()` not returning the constant

**Diagnostic checklist:**
```java
// Check Constants class
grep "SERVER_TYPE" {connector-id}/src/main/java/.../constants/{Prefix}Constants.java

// Check Connector class
grep "getServerType" {connector-id}/src/main/java/.../main/{Prefix}Connector.java
```

**Fix steps:**

1. Define in Constants:
```java
public static final String SERVER_TYPE = "{server-type}";  // lowercase, no spaces
```

2. Return in Connector:
```java
@Override
public String getServerType() {
    return {Prefix}Constants.SERVER_TYPE;
}
```

---

### Category 3: Connection Validation Failures

#### Symptom: `validateConnection()` always fails

**Possible causes:**
1. Null config not handled
2. Required attributes missing validation
3. `exchangeAttributes` mapping broken

**Diagnostic checklist:**
```java
// Check null handling
// In validateConnection():
if (config == null) {
    return new ValidateConnectionResponse()
        .withSuccess(false)
        .withValid(false)
        .withMessage("Connection configuration is required");
}
```

**Fix steps:**

1. Add null check at start of `validateConnection()`
2. Validate required attributes:
```java
String baseUrl = config.getAttributeValue("{KEY}");
if (baseUrl == null || baseUrl.isBlank()) {
    return new ValidateConnectionResponse()
        .withSuccess(false)
        .withValid(false)
        .withMessage("Base URL is required");
}
```

---

#### Symptom: Credentials work locally but fail in runtime

**Possible causes:**
- `exchangeAttributes` mapping doesn't preserve values
- Vault/credential manager configuration mismatch

**Diagnostic checklist:**
```java
// Test round-trip in unit test:
Map<String, ConnectionAttribute> attrs = connector.getAttributes();
attrs.get(KEY).setValue("test-value");
ConnInfo connInfo = connector.exchangeAttributes(attrs);
Map<String, ConnectionAttribute> roundTrip = connector.exchangeAttributes(connInfo);
// roundTrip.get(KEY).getValue() should equal "test-value"
```

**Fix steps:**

1. Ensure `exchangeAttributes(Map)` stores values in ConnInfo:
```java
connInfo.setAdditionalAttr(KEY, getAttribute(attrs, KEY));
```

2. Ensure `exchangeAttributes(ConnInfo)` restores values:
```java
attrs.get(KEY).setValue(connInfo.getAdditionalAttr().get(KEY));
```

---

### Category 4: Metadata Issues

#### Symptom: `getSupportedObjects()` returns empty or wrong types

**Possible causes:**
- Not using `ObjectKind` enum values
- Empty list returned

**Fix steps:**

Use `ObjectKind` enum values:
```java
@Override
public SupportedObjectsResponse getSupportedObjects() {
    List<SupportedObject> objects = List.of(
        new SupportedObject(ObjectKind.ENTITY.value(), "Tables", "Database tables"),
        new SupportedObject(ObjectKind.VIEW.value(), "Views", "Database views")
    );
    return new SupportedObjectsResponse().withSuccess(true).withSupportedObjects(objects);
}
```

---

#### Symptom: `getContainers()` returns empty list

**Possible causes:**
1. EntityType mismatch with `getSupportedObjects()`
2. Connection not working (auth failure)
3. API call returning empty results

**Diagnostic checklist:**
```java
// Log the request to see what's being asked for
LOG.info("getContainers called with entityType: {}", request.getEntityType());

// Verify entityType matches what getSupportedObjects returns
// If getSupportedObjects returns ObjectKind.ENTITY.value(), 
// getContainers will receive "Entity" as entityType
```

**Fix steps:**

1. Match entityType handling:
```java
@Override
public ContainersResponse getContainers(ContainersRequest request) {
    if (request == null) {
        return new ContainersResponse().withSuccess(false).withMessage("Request is required");
    }
    // entityType from request should match ObjectKind values
    String entityType = request.getEntityType();
    // Handle based on entityType...
}
```

---

#### Symptom: `getObjects()` returns empty or wrong objects

**Possible causes:**
1. Not dispatching by `entityType`
2. Not using filters for subtypes
3. Container ID mismatch

**Fix steps:**

Dispatch by entityType:
```java
@Override
public ObjectResponse getObjects(ObjectRequest request) {
    String entityType = request.getEntityType();
    
    if (ObjectKind.ENTITY.value().equals(entityType)) {
        return fetchTables(request);
    } else if (ObjectKind.INDEX.value().equals(entityType)) {
        return fetchIndexes(request);
    }
    // ... other types
    
    return new ObjectResponse().withSuccess(false)
        .withMessage("Unknown entity type: " + entityType);
}
```

Use filters for subtypes:
```java
// When multiple subtypes share one ObjectKind
List<Map<String, String>> filters = request.getFilters();
String subtype = filters.stream()
    .filter(f -> f.containsKey("objectSubType"))
    .map(f -> f.get("objectSubType"))
    .findFirst()
    .orElse("default");
```

---

#### Symptom: `getFields()` returns wrong fields

**Possible causes:**
1. Not dispatching by entityType
2. EntityId doesn't match what getObjects returned

**Fix steps:**

Dispatch by entityType:
```java
@Override
public FieldsResponse getFields(FieldsRequest request) {
    String entityType = request.getEntityType();
    String entityId = request.getEntityId();
    
    if (ObjectKind.ENTITY.value().equals(entityType)) {
        return fetchTableColumns(request);
    } else if (ObjectKind.INDEX.value().equals(entityType)) {
        return fetchIndexColumns(request);  // Returns column names in index
    }
    // ...
}
```

---

### Category 5: Query Issues

#### Symptom: `fetchData()` returns empty or wrong data

**Possible causes:**
1. EntityId doesn't match getObjects output
2. Query not built correctly
3. Pagination not handled

**Diagnostic checklist:**
```java
// Log the request
LOG.info("fetchData: entityType={}, containerId={}, entityId={}, limit={}, offset={}",
    request.getEntityType(), request.getContainerId(), 
    request.getEntityId(), request.getLimit(), request.getOffset());
```

**Fix steps:**

Ensure entityId consistency:
```java
// If getObjects returns entityId = "schema.tablename"
// Then fetchData must use the same format to query
```

---

#### Symptom: Row keys don't match field names

**Possible causes:**
- Field names in `getFields()` differ from keys in `fetchData()` rows

**Fix steps:**

Use consistent keys:
```java
// In getFields():
FieldInfo field = new FieldInfo()
    .withName("customer_id")  // This key...
    .withType("INTEGER");

// In fetchData():
Map<String, Object> row = new HashMap<>();
row.put("customer_id", value);  // ...must match this key
```

---

#### Symptom: Pagination doesn't work

**Possible causes:**
- `limit` and `offset` not respected
- `totalRows` incorrect

**Fix steps:**

```java
@Override
public QueryResponse fetchData(QueryRequest request) {
    int limit = request.getLimit() > 0 ? request.getLimit() : 100;
    int offset = request.getOffset() >= 0 ? request.getOffset() : 0;
    
    // Apply to query
    List<Map<String, Object>> allRows = queryAllRows();
    int totalRows = allRows.size();
    
    List<Map<String, Object>> pagedRows = allRows.stream()
        .skip(offset)
        .limit(limit)
        .collect(Collectors.toList());
    
    return new QueryResponse()
        .withSuccess(true)
        .withData(pagedRows)
        .withTotalRows(totalRows);
}
```

---

### Category 6: Test Failures

#### Symptom: Tests fail with NullPointerException

**Possible causes:**
- Service instances not initialized
- Mock not set up correctly

**Fix steps:**

Initialize in `@BeforeEach`:
```java
@BeforeEach
void setUp() {
    connector = new MyConnector();
}
```

---

#### Symptom: LegacyServerTypeForbiddenTest fails

**Possible causes:**
- ServerType conflicts with legacy platform types
- Duplicate serverType in repo
- `configs/{connector-id}.json` `connectorMaster.server` does not match `getServerType()`
- `@SdkConnector(artifactId)` does not match Maven `artifactId` / config basename

**Diagnostic checklist:**
```bash
# Check legacy server types
cat csp-api/src/main/resources/legacy-platform-server-types.txt | grep -i {server-type}

# Check for duplicates
grep -r "SERVER_TYPE.*=" */src/main/java/**/constants/*.java

# Check registration JSON server field
grep '"server"' {connector-id}/src/main/resources/configs/*.json
```

**Fix steps:**

1. Choose a unique serverType not in legacy list
2. Ensure only one connector uses that serverType
3. Ensure `configs/{connector-id}.json` exists and `connectorMaster.server` equals `getServerType()`
4. Ensure `@SdkConnector(artifactId = "{connector-id}")` matches module artifactId

---

### Category 7: Runtime Issues

#### Symptom: csp-api won't start (port in use)

**Fix steps:**
```bash
# Find what's using port 8800
lsof -i :8800

# Either kill the process or change port in application.properties
# csp-api/src/main/resources/application.properties
server.port=8801
```

---

#### Symptom: ClassNotFoundException at runtime

**Possible causes:**
- Dependency not included in assembly
- Shade plugin excluding needed classes

**Fix steps:**

1. Check assembly `pom.xml` has connector dependency
2. Check shade plugin configuration for unintended exclusions
3. Rebuild assembly: `mvn clean install -pl assembly -am`

---

## Quick Diagnostic Commands

```bash
# Full rebuild from scratch
mvn clean install -DskipTests

# Build specific connector with dependencies
mvn clean install -pl {connector-id} -am

# Run connector tests only
mvn test -pl {connector-id}

# Check if connector appears in csp-api
curl -s http://localhost:8800/v1/info | jq '.connectors[].serverType'

# Test connection validation (request body is ConnectionConfig; see csp-api/README.md)
curl -X POST http://localhost:8800/v1/connection/validate \
  -H "Content-Type: application/json" \
  -d '{"serverType":"{server-type}","connectionInfoId":1,"attributes":{"host":"localhost"}}'

# Run quick app standalone
mvn -pl {connector-id} spring-boot:run
```

---

## Debug Logging

Add temporary debug logging to trace issues:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger LOG = LoggerFactory.getLogger(MyConnector.class);

// In methods:
LOG.debug("validateConnection called with config: {}", 
    config != null ? config.getServerType() : "null");
```

**Important:** Remove sensitive data from logs before committing!

---

## Error Message Patterns

Common error messages and their meanings:

| Error Message | Likely Cause |
|---------------|--------------|
| "artifact not found" | Missing dependency or credentials |
| "SPI registration" | Missing/incorrect META-INF/services file |
| "serverType validation" | Legacy conflict or duplicate |
| "connection refused" | csp-api not running |
| "entityType" in message | Metadata routing issue |
| "required" in message | Missing connection attribute |

---

## Escalation Path

If debugging doesn't resolve the issue:

1. Check SDK Troubleshooting Guide: `.docs/sdk/12.SDK_Troubleshooting_Guide.md`
2. Review reference implementations: `monetdb/`, `zohodesk/`, `awsconsole/`
3. Run **connector-code-review** to catch contract gaps
4. Contact OvalEdge support: developer@ovaledge.com

## Related skills

- **connector-code-review** — systematic pre-PR validation
- **build-new-connector-sdk** — correct SPI, pom, and `@SdkConnector` patterns
