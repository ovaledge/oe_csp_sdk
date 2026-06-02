---
name: unit-test-generation
description: Generate comprehensive unit tests for OvalEdge CSP SDK connector implementations. Use when the user asks to create tests, add test coverage, or mentions testing for a connector.
---

# Unit Test Generation Skill for OvalEdge CSP SDK Connectors

Generate comprehensive unit tests for connector implementations following SDK patterns and quality gates.

## Mandatory behavior (strict)

- **Read implementation first**: Derive attribute keys, `ObjectKind` list, and validation rules from `{Prefix}Constants` and service code — do not assume API behavior.
- **No secrets in tests**: No real credentials, tokens, or production URLs in committed fixtures.
- **No trivial tests**: Every test must assert meaningful contract behavior (null handling, round-trip, dispatch), not merely "method exists".

---

## When to Use

- User asks to "generate tests", "add tests", or "create unit tests" for a connector
- User mentions test coverage for `AppsConnector`, `MetadataService`, or `QueryService`
- User needs tests for PR submission (required for public catalog)
- User asks to expand skeleton test TODOs in generated connector

---

## Test File Locations

Tests should be placed in the connector module's test directory:

```
{connector-id}/src/test/java/com/ovaledge/csp/apps/{package}/main/
├── {Prefix}ConnectorTest.java
├── {Prefix}MetadataServiceTest.java
└── {Prefix}QueryServiceTest.java
```

---

## Test Framework and Dependencies

All tests use **JUnit 5** with **Mockito**:

```java
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
```

---

## Required Test Categories

### 1. ConnectorTest — `{Prefix}ConnectorTest.java`

Must test all `AppsConnector` contract methods:

| Test Method | Purpose |
|-------------|---------|
| `getServerType_returnsConnectorServerType()` | Verifies stable, unique server type |
| `getAttributes_containsRequiredKeys()` | Verifies all connector-specific attributes are present |
| `getAttributes_secretFieldsAreMasked()` | Verifies sensitive fields (passwords, tokens) have `isMasked() == true` |
| `validateConnection_nullConfigReturnsFailure()` | Null input handling |
| `validateConnection_missingRequiredAttributeReturnsFailure()` | Missing required fields |
| `validateConnection_validConfigReturnsSuccess()` | Happy path with mocked/valid credentials |
| `getMetadataService_returnsNonNullInstance()` | Service accessor |
| `getQueryService_returnsNonNullInstance()` | Service accessor |
| `exchangeAttributes_roundTrip_preservesValues()` | Map → ConnInfo → Map preserves all values |
| `exchangeAttributes_setsCorrectServerType()` | ConnInfo has correct servertype |

**Example pattern:**

```java
@ExtendWith(MockitoExtension.class)
class ZohodeskConnectorTest {

    private ZohodeskConnector connector;

    @BeforeEach
    void setUp() {
        connector = new ZohodeskConnector();
    }

    @Test
    void getServerType_returnsZohoDesk() {
        assertEquals(ZohodeskConstants.SERVER_TYPE, connector.getServerType());
    }

    @Test
    void getAttributes_containsRequiredKeys() {
        Map<String, ConnectionAttribute> attrs = connector.getAttributes();
        assertNotNull(attrs);
        assertTrue(attrs.containsKey(ZohodeskConstants.KEY_BASE_URL));
        assertTrue(attrs.containsKey(ZohodeskConstants.KEY_CLIENT_ID));
        assertTrue(attrs.containsKey(ZohodeskConstants.KEY_CLIENT_SECRET));
        // Assert masked fields
        assertTrue(attrs.get(ZohodeskConstants.KEY_CLIENT_SECRET).isMasked());
    }

    @Test
    void validateConnection_missingConfigReturnsFailure() {
        ValidateConnectionResponse response = connector.validateConnection(null);
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertFalse(response.isValid());
    }

    @Test
    void exchangeAttributes_roundTrip_preservesConnectorSpecificValues() {
        Map<String, ConnectionAttribute> attrs = connector.getAttributes();
        attrs.get(ZohodeskConstants.KEY_BASE_URL).setValue("https://api.example.com");
        attrs.get(ZohodeskConstants.KEY_CLIENT_ID).setValue("test-client");

        ConnInfo connInfo = connector.exchangeAttributes(attrs);
        assertNotNull(connInfo);
        assertEquals(ZohodeskConstants.SERVER_TYPE, connInfo.getServertype());

        Map<String, ConnectionAttribute> roundTrip = connector.exchangeAttributes(connInfo);
        assertEquals("https://api.example.com", roundTrip.get(ZohodeskConstants.KEY_BASE_URL).getValue());
        assertEquals("test-client", roundTrip.get(ZohodeskConstants.KEY_CLIENT_ID).getValue());
    }
}
```

---

### 2. MetadataServiceTest — `{Prefix}MetadataServiceTest.java`

Must test all `MetadataService` contract methods:

| Test Method | Purpose |
|-------------|---------|
| `getSupportedObjects_returnsSuccessfulResponse()` | Returns success=true |
| `getSupportedObjects_returnsExpectedObjectKinds()` | Verifies correct ObjectKind values and display names |
| `getContainers_nullRequestReturnsFailure()` | Null handling |
| `getContainers_returnsContainersForValidRequest()` | Happy path |
| `getObjects_nullRequestReturnsFailure()` | Null handling |
| `getObjects_returnsObjectsForContainer()` | Happy path with mock |
| `getObjects_dispatchesByEntityType()` | Verifies dispatch by ObjectKind |
| `getFields_nullRequestReturnsFailure()` | Null handling |
| `getFields_returnsFieldsForObject()` | Happy path |

**For connectors with helper methods, test those too:**

```java
@Test
void resolveSubtype_prefersObjectSubtypeFilter() {
    String subtype = ZohodeskMetadataService.resolveSubtype(
            List.of(Map.of(ZohodeskConstants.FILTER_OBJECT_SUBTYPE, "agents")),
            "tickets");
    assertEquals("agents", subtype);
}

@Test
void inferType_mapsPrimitiveAndCompositeValues() {
    assertEquals("INTEGER", ZohodeskMetadataService.inferType(100));
    assertEquals("FLOAT", ZohodeskMetadataService.inferType(10.5d));
    assertEquals("BOOLEAN", ZohodeskMetadataService.inferType(true));
    assertEquals("ARRAY", ZohodeskMetadataService.inferType(List.of("a")));
    assertEquals("OBJECT", ZohodeskMetadataService.inferType(Map.of("k", "v")));
    assertEquals("STRING", ZohodeskMetadataService.inferType("value"));
}
```

---

### 3. QueryServiceTest — `{Prefix}QueryServiceTest.java`

Must test `QueryService.fetchData()`:

| Test Method | Purpose |
|-------------|---------|
| `fetchData_nullRequestReturnsFailure()` | Null handling |
| `fetchData_missingEntityTypeReturnsFailure()` | Validates required fields |
| `fetchData_missingContainerIdReturnsFailure()` | Validates required fields |
| `fetchData_missingEntityIdReturnsFailure()` | Validates required fields |
| `fetchData_validRequestReturnsRows()` | Happy path with mock |
| `fetchData_respectsLimitAndOffset()` | Pagination |
| `fetchData_columnNamesMatchRowKeys()` | Schema consistency |

**Example pattern:**

```java
@ExtendWith(MockitoExtension.class)
class ZohodeskQueryServiceTest {

    private ZohodeskQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new ZohodeskQueryService();
    }

    @Test
    void fetchData_nullRequestReturnsFailure() {
        QueryResponse response = queryService.fetchData(null);
        assertNotNull(response);
        assertFalse(response.getSuccess());
    }

    @Test
    void fetchData_missingEntityTypeReturnsFailure() {
        QueryRequest request = QueryRequest.builder()
                .withContainerId("container")
                .withEntityId("entity")
                .build();
        QueryResponse response = queryService.fetchData(request);
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().toLowerCase().contains("entitytype"));
    }
}
```

---

## Edge Cases Checklist (Must Test)

From the Connector Interface Reference, these edge cases MUST be handled:

1. **Null/blank required identifiers** (`entityType`, `containerId`, `entityId`)
2. **Unknown `entityType`** passed to metadata/query paths
3. **Empty container/object sets** (return success with empty lists)
4. **Partial field projections** (requested fields missing or unsupported)
5. **Pagination bounds** (`limit <= 0`, negative `offset`, oversized page request)
6. **Credential manager selected but vault path/secrets missing**
7. **Auth token expired** during metadata/query

---

## Mocking ConnectionConfig

When tests need `ConnectionConfig`, build it with the builder:

```java
ConnectionConfig config = ConnectionConfig.builder()
        .withConnectionInfoId(100)
        .withServerType("myconnector")
        .withAttribute("key", "value")
        .build();
```

For tests that need real connection behavior, mock the external client or use integration test markers.

---

## Test Naming Convention

Follow the pattern: `methodName_condition_expectedBehavior`

Examples:
- `getServerType_returnsConnectorServerType()`
- `validateConnection_nullConfig_returnsFailure()`
- `getObjects_validContainerId_returnsObjectList()`
- `fetchData_negativeLimitValue_returnsValidationError()`

---

## Reactor validation tests

From repo root, these may fail if `serverType` or `@SdkConnector` metadata is wrong:

```bash
mvn test -pl assembly -Dtest=LegacyServerTypeForbiddenTest
```

See **connector-debugging** (Category 6: `LegacyServerTypeForbiddenTest fails`) for fixes.

---

## Quality Gates for Tests

Before PR submission, tests must:

- [ ] Cover happy path for all public methods
- [ ] Cover error scenarios (null inputs, missing required fields)
- [ ] Cover edge cases from the interface reference
- [ ] Pass `mvn test` with no failures
- [ ] Not contain hardcoded credentials or secrets
- [ ] Not log sensitive information during test execution

---

## Generating Tests — Step by Step

1. **Read the connector implementation** to understand:
   - What attributes are defined in Constants
   - What ObjectKinds are supported
   - What entities/containers exist
   - What helper methods need testing

2. **Generate ConnectorTest first** — covers the entry point and attribute handling

3. **Generate MetadataServiceTest** — covers discovery flow

4. **Generate QueryServiceTest** — covers data fetch flow

5. **Run tests** to verify: `mvn test -pl {connector-id}`

6. **Check coverage** of edge cases from the checklist above

---

## Integration with Existing Tests

The connector archetype generates skeleton tests with TODOs. When expanding these:

1. Keep the existing test method signatures
2. Replace `fail("TODO")` or skeleton assertions with real tests
3. Add new test methods for edge cases
4. Do not remove existing test methods unless they are duplicates

---

## Example: Complete Test Suite for a REST Connector

See `zohodesk/src/test/java/com/ovaledge/csp/apps/zohodesk/main/` for a complete reference implementation with:

- Connector tests with round-trip attribute exchange
- MetadataService tests with subtype resolution and type inference
- QueryService tests with pagination validation

## Related skills

- **connector-code-review** — Phase 7 test expectations before PR
- **build-new-connector-sdk** — contracts tests must enforce
- **connector-debugging** — when `mvn test -pl {connector-id}` or `LegacyServerTypeForbiddenTest` fails
