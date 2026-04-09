# OvalEdge Connector SDK for Java

Build custom OvalEdge connectors as modular Java components to integrate external systems (databases, APIs, SaaS apps, ERPs, CRMs) into OvalEdge metadata and query workflows.

This repository provides:
- A connector development pattern (`AppsConnector` + `MetadataService` + `QueryService`)
- Two scaffolding paths: Maven archetype (CLI) and `Create New Connector` from UI
- A unified Spring Boot API (`csp-api`) to test connectors through one runtime
- Reference connector implementations (for example, `monetdb`, `awsconsole`)
- An assembly module to package connectors for deployment

## Who This Is For

- Partners, customers, and internal developers building OvalEdge connectors
- Java engineers familiar with APIs/JDBC/authentication patterns
- Teams that need private or publishable connector implementations

## Prerequisites

- JDK `21`
- Maven `3.8+`
- Git
- Access to required dependencies and credentials (for private repositories, if applicable)

## Repository Structure

- `csp-api` - Unified REST runtime for connector testing (`/v1/*`)
- `connector-archetype` - Maven archetype to generate a new connector module
- `assembly` - Builds deployable assembly JAR with connector discovery metadata
- `monetdb` - JDBC-style reference implementation
- `awsconsole` - API-style reference implementation
- `.docs` - Extended SDK documentation

## Quick Start (5 Minutes)

1. Clone the repository and open at project root.
2. Build all modules:

```bash
mvn clean install -DskipTests
```

3. Run unified test runtime:

```bash
java -jar csp-api/target/csp-api-1.0.0-SNAPSHOT.jar
```

4. Verify available connectors:

```bash
curl http://localhost:8800/v1/info
```

5. Validate connection (example using `monetdb`):

```bash
curl -X POST http://localhost:8800/v1/connection/validate \
  -H "Content-Type: application/json" \
  -d '{
    "serverType": "monetdb",
    "attributes": {
      "host": "localhost",
      "port": "50000",
      "database": "demo",
      "username": "monetdb",
      "password": "monetdb"
    }
  }'
```

## Build a New Connector

You can scaffold a new connector using either approach:

- **Option A (CLI):** Maven archetype (`connector-archetype`)
- **Option B (UI):** `Create New Connector` flow in `csp-api` UI

### 1) Generate Connector Scaffold (Option A: CLI)

Install archetype once from repository root:

```bash
mvn clean install -pl connector-archetype -am
```

Generate a new connector module using the archetype (non-interactive recommended in CI/local scripts).

### 1b) Generate Connector Scaffold (Option B: UI)

1. Start `csp-api`.
2. Open the UI in your browser.
3. Click **Create New Connector**.
4. Provide connector details and generate/download the scaffold package.
5. Place generated sources in repository root if you downloaded as ZIP, then import as Maven module.

Use the CLI path if you prefer scriptable/repeatable generation. Use the UI path for faster guided setup.

### 2) Implement Connector Contracts

Every connector module should implement:
- `AppsConnector`
- `MetadataService`
- `QueryService`

At minimum, ensure:
- Unique lowercase `serverType` (for request routing)
- Connection validation logic
- Supported objects, containers, objects, and fields metadata flow
- Query execution (`fetchData`) with sane pagination behavior

### 3) Register with ServiceLoader

Add connector implementation to:

`META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector`

Without this registration, runtime discovery will fail.

### 4) Add to Runtime

Add your module dependency in `csp-api/pom.xml`, rebuild, restart `csp-api`, then verify using:

```bash
curl http://localhost:8800/v1/info
```

## Development Checklist

- Connector classes compile and are discoverable via ServiceLoader
- `serverType` is unique and stable
- Metadata APIs return deterministic, paginatable responses
- Query logic supports limits/offsets and safe filtering where applicable
- Error responses are actionable (auth/network/schema issues are distinguishable)
- No sensitive credentials are printed in logs

## Testing

Common build/test commands:

```bash
# Build all modules
mvn clean install -DskipTests

# Build a specific connector and dependencies
mvn clean install -pl monetdb -am

# Run tests
mvn test
```

Recommended test coverage per connector:
- Connection success/failure scenarios
- Metadata traversal (supported objects -> containers -> objects -> fields)
- Query behavior (pagination, empty sets, invalid entity/container inputs)
- Error handling for source outages and invalid credentials

## Packaging and Deployment

- Use module build for local connector iteration
- Use `assembly` for deployable SDK bundle consumption
- If available in your environment, use helper scripts (for example, `build-csp-sdk.sh`) from repository root

Before release:
- Version bump per release policy
- Update connector config metadata and icons (if applicable)
- Validate on a clean environment with production-like credentials

## Security and Operational Guidance

- Keep secrets in secure stores and environment variables; avoid hardcoding
- Mask tokens/passwords in logs and error payloads
- Use least-privilege source credentials
- Apply connection and query timeouts to avoid hanging calls
- Validate and sanitize dynamic query inputs

## Troubleshooting

- Connector not listed in `/v1/info`
  - Check `META-INF/services` registration and classpath/module dependency
- `No connector found for serverType`
  - Confirm request `serverType` matches connector `getServerType()`
- Build succeeds but runtime fails
  - Rebuild with `-am`, restart runtime, verify dependency inclusion in `csp-api`
- Authentication failures
  - Verify source credentials/tenant/account scope and connector attribute mapping
- Metadata empty unexpectedly
  - Validate source permissions and object-type filters

## Reference Implementations

- `monetdb` - JDBC-style connector pattern
- `awsconsole` - API-style connector pattern

Use these modules as implementation templates when building new connectors.

## Additional Documentation

- Complete SDK guide: [`OvalEdge Connectors Software Development Kit`](.docs/OvalEdge%20Connectors%20Software%20Development%20Kit.md)

