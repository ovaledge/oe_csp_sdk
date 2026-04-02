# csp-api

Unified Spring Boot application for testing all Apps connectors through a single entry point.

## Overview

The `csp-api` module provides a unified REST API that automatically discovers and routes requests to all available connectors via Java's ServiceLoader mechanism. This allows you to test all connectors (MonetDB, etc.) through a single application without needing to run individual connector applications.

## Features

- **Automatic Connector Discovery**: Discovers all connectors via ServiceLoader at startup
- **Unified API**: Single REST API endpoint (`/v1/*`) for all connectors
- **Dynamic Routing**: Routes requests to the appropriate connector based on `serverType` in the request
- **Backward Compatible**: Individual connector applications can still be run standalone

## Building

```bash
# Build the csp-api module
mvn -pl csp-api clean package -DskipTests

# Build all modules including csp-api
mvn clean install -DskipTests
```

## Running

```bash
# Run the packaged JAR
java -jar csp-api/target/csp-api-1.0.0-SNAPSHOT.jar
```

The application will start on port 8800 by default.

## API Endpoints

All endpoints are prefixed with `/v1/`

### Connection Endpoints
- `POST /v1/connection/validate` - Validate connection

### Metadata Endpoints
- `POST /v1/metadata/supported-objects` - Get supported objects (entities, reports, etc.)
- `POST /v1/metadata/containers` - Get containers (companies, organizations, etc.)
- `POST /v1/metadata/objects` - Get objects (entities or reports) under a container
- `POST /v1/metadata/fields` - Get fields for an entity or report

### Query Endpoints
- `POST /v1/query` - Execute query to fetch entity or report data

### Info Endpoint
- `GET /v1/info` - Get information about available connectors

## Request Format

All requests must include `serverType` in the `ConnectionConfig` within the request body. The controller automatically extracts `serverType` and routes to the appropriate connector.

Example request body for connection validation:
```json
{
  "serverType": "monetdb",
  "attributes": {
    "host": "localhost",
    "port": "50000",
    "database": "demo",
    "username": "monetdb",
    "password": "monetdb"
  }
}
```

Example request body for query:
```json
{
  "connectionConfig": {
    "serverType": "monetdb"
  },
  "entityType": "entity",
  "containerId": "company-id",
  "entityId": "Customer",
  "fields": ["Name", "Email", "Phone"],
  "filters": []
}
```

## Routing Logic

The service layer (`AppsServiceImpl`) extracts `serverType` from the request's `ConnectionConfig`:
- All request types contain `ConnectionConfig`
- The service calls `appsRegistry.getConnector(serverType)` to get the appropriate connector
- If `serverType` is missing or no connector is found, a `RuntimeException` is thrown with details

The controller layer (`ConnectorController`) delegates all requests to `AppsService`, which handles connector lookup and routing automatically.

## Available Connectors

To see all available connectors, call:
```bash
curl http://localhost:8080/v1/info
```

This returns:
```json
{
  "availableConnectors": ["monetdb"],
  "connectorCount": 2
}
```

## Individual Connector Applications

Individual connector applications (if present) can be run standalone for testing in isolation. Connectors are discovered via ServiceLoader from the csp-api or assembly JAR.

These are useful for:
- Testing individual connectors in isolation
- Using connector-specific endpoints
- Development and debugging

## Architecture

```
csp-api
├── CspAppsApiApplication (Spring Boot main class)
├── AppsRegistry (ServiceLoader-based discovery)
├── AppsService (Service interface)
├── AppsServiceImpl (Service implementation - delegates to connectors)
└── ConnectorController (REST controller)
```

The architecture follows:
- **Controller Layer**: `ConnectorController` uses `Callable` for async processing and delegates to service layer
- **Service Layer**: `AppsService` interface and `AppsServiceImpl` implementation handle business logic
- **Connector Discovery**: `AppsRegistry` uses Java's `ServiceLoader` to discover all `AppsConnector` implementations registered in `META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector`

## Adding New Connectors

When you add a new connector module:
1. Ensure it implements `AppsConnector` interface
2. Register it in `META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector`
3. Add it as a dependency in `csp-api/pom.xml`
4. Rebuild and restart - the new connector will be automatically discovered

## Logging

Logging is configured via `application.properties`. Connector discovery and routing information is logged at INFO level.

## Configuration

Server port and other settings can be configured in `src/main/resources/application.properties`:

```properties
server.port=8800
server.servlet.context-path=/
```
