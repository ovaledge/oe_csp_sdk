You are a technical researcher tasked with gathering all the information needed to build an OvalEdge connector for: **[CONNECTOR_NAME]**
OvalEdge connectors are Java Maven modules that integrate external data sources (databases, APIs, ERPs, CRMs, cloud platforms, BI tools, etc.) into the OvalEdge data governance platform. Each connector must implement three core contracts:
1. **AppsConnector** — handles connection validation, authentication, and connection attribute definitions
2. **MetadataService** — discovers and crawls metadata (containers, objects/entities, fields/columns)
3. **QueryService** — fetches actual data rows from the source system
Research and provide the following details for **[CONNECTOR_NAME]**:
---
### 1. Product Overview
- What is [CONNECTOR_NAME]? What category does it fall into (database, CRM, ERP, BI tool, cloud platform, file system, etc.)?
- Who is the vendor? What is the official documentation URL?
- What editions/tiers exist (free, pro, enterprise)? Do API capabilities differ by tier?
### 2. Authentication & Connectivity
Identify ALL supported authentication mechanisms and provide details for each:
- **Authentication types**: Which of the following does it support?
  - API Key / Token
  - OAuth 2.0 (Authorization Code, Client Credentials, etc.)
  - Basic Auth (username/password)
  - JDBC connection (host, port, database, driver)
  - Service Account / Key File
  - SAML / SSO
  - Certificate-based
  - Custom/proprietary auth
- **For each auth type, specify**:
  - Required credentials/parameters (e.g., host, port, API key, client ID, client secret, tenant ID, access token URL, etc.)
  - Which parameters should be marked as sensitive/masked (passwords, secrets, tokens)
  - Token refresh mechanism (if OAuth)
  - Base URL / endpoint pattern
  - Any required headers or query parameters for authentication
- **Rate limiting**: Are there API rate limits? What are the thresholds? How should the connector handle rate limiting (backoff, retry)?
- **Networking**: Does it require special network configuration (VPN, IP whitelisting, proxy support)?
### 3. Data Model & Object Hierarchy
Map out the full object/entity hierarchy that [CONNECTOR_NAME] exposes. For each level, specify:
#### 3.1 Containers (top-level groupings)
What are the top-level containers? Examples from other connectors:
- Database schemas
- AWS regions / accounts
- Workspaces / projects
- Companies / organizations
- Folders / directories
For [CONNECTOR_NAME], list:
- Container type(s) and their names
- API endpoint(s) to list/discover containers
- Whether containers support pagination
- Key attributes of each container (ID, name, description, etc.)
#### 3.2 Objects / Entities (data objects within containers)
What types of objects/entities exist? Map each to the appropriate OvalEdge ObjectKind:
- **ENTITY** — tables, records, data objects
- **VIEW** — views, virtual tables
- **REPORT** — reports
- **DASHBOARD** — dashboards
- **FILE** — files
- **FILEFOLDERS** — folders, buckets
- **FUNCTION** — stored functions
- **PROCEDURE** — stored procedures
- **SEQUENCE** — sequences
- **INDEX** — indexes
- **TRIGGER** — triggers
- **CONTAINER** — sub-containers
For each object type, provide:
- Object type name and display name
- API endpoint(s) to list/discover objects of this type within a container
- Whether the list supports pagination (and pagination mechanism: offset, cursor, page token, next link)
- Key attributes (ID, name, description, type, created date, modified date, owner, etc.)
- Whether objects are discovered statically (fixed enum list) or dynamically (via API)
- Any subtypes or categories within this object type
#### 3.3 Fields / Columns (attributes of each object)
For each object type, what fields/columns does it expose?
- API endpoint(s) to retrieve fields for a given object
- Field attributes: name, data type, position/ordinal, nullable, description/comment, default value, primary key, foreign key
- Are fields discovered dynamically (via API/metadata endpoint) or defined statically?
- Data type mappings (source types to standard types like STRING, INTEGER, FLOAT, DATE, BOOLEAN, etc.)
### 4. Required APIs & Endpoints
List ALL API endpoints needed for the connector, grouped by purpose:
#### 4.1 Connection Validation
- Which endpoint(s) can be used to verify credentials and connectivity?
- Expected success/failure response patterns
#### 4.2 Metadata Discovery
- Endpoints for listing containers
- Endpoints for listing objects per container per type
- Endpoints for listing fields per object
- Endpoints for extended validation (object counts per container)
#### 4.3 Data Querying
- Endpoint(s) for fetching actual data/records from an object
- Query parameters: fields selection, filtering, sorting, limit, offset
- Response format (JSON, XML, CSV, etc.)
- Pagination mechanism for data results
- Maximum page/batch size limits
#### 4.4 API Details
For each endpoint, specify:
- HTTP method (GET, POST, PUT, etc.)
- URL pattern (with path parameters)
- Required headers
- Request body format (if POST)
- Response body format and key fields
- Error response patterns and status codes
### 5. Pagination Patterns
How does [CONNECTOR_NAME] handle pagination? Map to OvalEdge pagination model:
- **Offset-based**: offset + maxResults
- **Page-based**: page + pageSize
- **Cursor/token-based**: pageToken / nextPageToken
- **Link-based**: nextLink URL
- What is the maximum page size?
- How to detect "has more" results?
- How to get total count?
### 6. SDK / Client Libraries
- Is there an official Java SDK or client library? Maven coordinates?
- Is there a REST API that can be called directly via HTTP?
- Is there a JDBC driver available? Maven coordinates and JDBC URL format?
- Any other supported protocols (GraphQL, gRPC, SOAP/XML, etc.)?
### 7. Connection Attributes Definition
Based on the authentication and connectivity research, define the complete list of connection attributes for the OvalEdge connection form:
For each attribute, specify:
- **Key** (constant name, e.g., `MYAPP_HOST`)
- **Label** (UI display name, e.g., "Host")
- **Description** (help text)
- **Type**: TEXT, PASSWORD, DROPDOWN, CHECKBOX
- **Required**: yes/no
- **Masked/Secret**: yes/no (for passwords, tokens, secrets)
- **Default value** (if any)
- **Display order** (priority for UI ordering)
- **Validation rules** (e.g., must be a valid URL, numeric port range)
### 8. Special Considerations
- Are there any known limitations or quirks of the API?
- Are there deprecated API versions that should be avoided?
- Are there specific API versioning requirements?
- Does the connector need to handle eventual consistency?
- Are there any data transformation requirements (e.g., flattening nested JSON)?
- Does the source system support webhooks or change detection?
- Are there any licensing considerations for API access?
### 9. Recommended Implementation Approach
Based on all the research above, recommend:
- The best authentication mechanism to implement first
- Whether to use an SDK, REST API, or JDBC
- The recommended pagination strategy
- Any helper utilities or third-party libraries needed
- Key challenges or risks in implementation
### 10. Summary Table
Provide a summary table with:
| Aspect | Details |
|--------|---------|
| Product Category | (e.g., CRM, ERP, Database, BI Tool) |
| Auth Type(s) | (e.g., OAuth 2.0, API Key) |
| API Protocol | (e.g., REST, JDBC, GraphQL) |
| Container Type(s) | (e.g., Workspaces, Schemas) |
| Object Types | (e.g., Tables, Reports, Dashboards) |
| Field Discovery | (Static / Dynamic) |
| Pagination | (Offset / Cursor / Page Token) |
| Java SDK Available | (Yes/No + Maven coordinates) |
| JDBC Driver | (Yes/No + Maven coordinates) |
| Rate Limits | (e.g., 100 req/min) |
| API Docs URL | (link) |