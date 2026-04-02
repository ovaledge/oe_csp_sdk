# MonetDB Connector (CSP SDK)

Apps connector for **MonetDB** via JDBC. Uses `ConnectionPoolManager` (csp-sdk-core) for pooled JDBC connections. No separate Client class; `ConnectionConfig` is used as-is (caller provides it). Metadata and Query services obtain the JDBC resource directly and run SQL inline.

- **Connector name:** MonetDB  
- **Server type:** `monetdb`  
- **Data source:** MonetDB with JDBC  

## Connection attributes

- Host, Port (default 50000), Database, Username, Password  

## Metadata

- **Supported objects:** Tables, Views (based on what the connector supports).
- **Containers:** Schemas (excluding `sys`, `tmp`).
- **Objects:** Tables or views per schema (by object type).
- **Fields:** Columns per table/view (from `sys.columns`).

## Query

- **fetchData:** SELECT with LIMIT/OFFSET; total row count from COUNT(*).

## Icon

Add `src/main/resources/static/monetdb.png` for the UI connector icon (optional).

## Build

From repo root:

```bash
mvn clean install -pl monetdb -am
```
