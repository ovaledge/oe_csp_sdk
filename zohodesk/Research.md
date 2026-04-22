# Zoho Desk Connector — Research

**Connector name:** Zoho Desk Connector  
**API type:** REST  
**Primary API reference:** [https://desk.zoho.com/api/v1/](https://desk.zoho.com/api/v1/) (US; see [Regional domains](#2-base-urls-and-regional-domains))  
**Official documentation hub:** [Zoho Desk API Documentation](https://desk.zoho.com/DeskAPIDocument) (also mirrored per region, e.g. `desk.zoho.eu/DeskAPIDocument`)

---

## 1. Product overview

| Item | Details |
|------|---------|
| **What it is** | Zoho Desk is a cloud customer support / help desk product (ticketing, SLAs, knowledge base, automation). |
| **Category** | SaaS **CRM / Customer support** (not a database; REST APIs expose business objects). |
| **Vendor** | Zoho Corporation. |
| **Editions** | Documented tiers include **Free/Trial**, **Express**, **Standard**, **Professional**, **Enterprise**, plus bundles such as **Zoho One** / **CRM Plus**. |
| **API vs edition** | **Yes — differences matter.** Example: `LICENSE_ACCESS_LIMITED` can reference edition (`FREE`, `EXPRESS`, `STANDARD`, `PROFESSIONAL`, `ENTERPRISE`). **API credits** (daily) and **concurrency limits** (simultaneous calls) vary by edition; Free/Trial has the lowest allocations. |

---

## 2. Authentication (OAuth 2.0, scopes, token refresh)

### 2.1 Supported mechanisms

| Mechanism | Supported for Desk REST API |
|-----------|------------------------------|
| **OAuth 2.0** (authorization code grant; Self Client for server/back-office) | **Primary / required** for production API access |
| API key only | **Not** the standard model for Desk v1 REST |
| Basic auth | **No** (password auth not used for API in documented flow) |
| JDBC | **N/A** |

### 2.2 OAuth 2.0 flow (summary)

1. **Register an app** in [Zoho API Console](https://api-console.zoho.com/) (client types: Web-based, Self Client, etc.).
2. **Authorization request** (browser redirect for web apps; or generated code for Self Client):

   `GET https://accounts.zoho.com/oauth/v2/auth`  
   Query parameters include: `response_type=code`, `client_id`, `redirect_uri`, **`scope`** (comma-separated Desk scopes), **`access_type`** (`offline` to receive a **refresh token**), `state`.

   After consent, the redirect includes **`code`** (grant token), and often **`location`** / **`accounts-server`** for the user’s data center — use these for token and API base selection in **multi–data center** setups ([Zoho multi-DC OAuth](https://www.zoho.com/accounts/protocol/oauth/multi-dc.html)).

3. **Exchange code for tokens** — `POST` to the **accounts server** for that user/region, e.g.:

   `POST https://accounts.zoho.com/oauth/v2/token`  
   `grant_type=authorization_code`, plus `code`, `client_id`, `client_secret`, `redirect_uri`.

4. **Use the access token** on Desk API requests:

   `Authorization: Zoho-oauthtoken <access_token>`

5. **Refresh access token** (access tokens are **short-lived**, documented as ~**1 hour**):

   `POST https://accounts.zoho.com/oauth/v2/token`  
   `grant_type=refresh_token`, plus `refresh_token`, `client_id`, `client_secret`, and typically `redirect_uri` / `scope` as registered.

6. **Revoke** (optional):

   `POST https://accounts.zoho.com/oauth/v2/token/revoke?token=<refresh_token>`

### 2.3 Important OAuth constraints (from official docs)

| Topic | Detail |
|-------|--------|
| Grant code validity | Grant token valid only **~1 minute** (exchange promptly). |
| Refresh token | Issued when `access_type=offline`; **long-lived** until user revokes. |
| Limits | Max **5 refresh tokens generated per user per minute**; a user can hold up to **20** refresh tokens; each refresh token up to **15** active (non-expired) access tokens — oldest rotated when exceeded. |
| Org binding | Token is bound to a Zoho Desk **organization (portal)**. **`orgId` header** must match the bound portal or **`OAUTH_ORG_MISMATCH`** occurs. |

### 2.4 Scopes (representative list)

Scopes are **`Desk.<resource>.<operation>`** style. Examples from the official scope list:

| Scope | Purpose |
|-------|---------|
| `Desk.tickets.READ` / `WRITE` / `UPDATE` / `CREATE` / `DELETE` / `ALL` | Tickets and related data |
| `Desk.contacts.READ` / `WRITE` / `UPDATE` / `CREATE` | Contacts, **accounts**, related data |
| `Desk.tasks.*` | Tasks |
| `Desk.basic.READ` / `CREATE` | **Organizations**, **agents**, **departments**, other basic entities |
| `Desk.settings.*` | Settings |
| `Desk.search.READ` | Search APIs |
| `Desk.events.*` | Event subscriptions |
| `Desk.articles.*` | Knowledge base articles |

**Connector guidance:** For read-only governance/crawl use cases, a minimal set is often **`Desk.basic.READ`** + **`Desk.tickets.READ`** + **`Desk.contacts.READ`** + **`Desk.search.READ`** (adjust if tasks/articles/settings are needed).

### 2.5 Sensitive connection attributes

Treat as **secret / masked**: `client_secret`, `refresh_token`, `access_token`, any grant codes.

---

## 3. Base URLs and regional domains

### 3.1 Desk API host (REST)

Pattern: **`https://desk.zoho.<tld>/api/v1/`**

Common mappings (align **Desk** host with the portal’s **data center**):

| Region | Example Desk API base |
|--------|------------------------|
| US (default) | `https://desk.zoho.com/api/v1/` |
| EU | `https://desk.zoho.eu/api/v1/` |
| India | `https://desk.zoho.in/api/v1/` |
| Australia | `https://desk.zoho.com.au/api/v1/` (verify in console / docs for your account) |
| China | `https://desk.zoho.com.cn/api/v1/` (verify for CN deployments) |

The Java SDK uses a **`dc`** (domain code) setting (`com`, `eu`, `in`, `au`, `cn`, etc.) to route requests — same idea applies to raw REST.

### 3.2 Zoho Accounts (OAuth) host

Must match the **same data center** as the user’s account, e.g.:

| DC | OAuth token endpoints (same paths as US) |
|----|------------------------------------------|
| US | `https://accounts.zoho.com/oauth/v2/auth`, `.../oauth/v2/token`, `.../oauth/v2/token/revoke` |
| EU | `https://accounts.zoho.eu/...` |
| IN | `https://accounts.zoho.in/...` |
| AU | `https://accounts.zoho.com.au/...` |
| (others) | See [Zoho Accounts multi-DC](https://www.zoho.com/accounts/protocol/oauth/multi-dc.html) |

### 3.3 Required headers (typical)

| Header | When |
|--------|------|
| `Authorization: Zoho-oauthtoken <token>` | Almost all calls |
| `orgId: <organization_id>` | **All Desk endpoints except** those explicitly documented as org-discovery without it (e.g. **`GET /organizations`** may omit `orgId`; otherwise **`orgId` is mandatory** per official introduction) |

---

## 4. Pagination model

### 4.1 Mechanism

Zoho Desk list APIs use **offset-style** query parameters:

| Parameter | Meaning |
|-----------|---------|
| **`from`** | **1-based** index of the first record to return (default **1**). |
| **`limit`** | Page size (default often **10**). |

Example: `GET .../accounts?from=1&limit=50`

Increment **`from`** by **`limit`** for the next page (or use `from = previous_from + limit`).

### 4.2 Limits and “has more”

| Topic | Detail |
|-------|--------|
| Max **`limit`** | Documentation states listing APIs typically allow up to **50** records per request; **some** APIs document a higher cap — **follow each endpoint’s doc**. |
| **Has more** | Often inferred when the number of returned records equals **`limit`** — fetch next page and stop when fewer than **`limit`** rows return (unless API returns explicit counts — see below). |
| **Total count** | Some resources expose **count** endpoints (e.g. **`/ticketsCount`**, **`/agents/count`**, **`/departments/count`**) — use where available for exact totals. |

### 4.3 OvalEdge mapping

Map to **offset + page size** semantics: internally store **0-based offset** as `(from - 1)` if the connector normalizes to zero-based indexing.

---

## 5. Rate limits, API credits, and throttling

Zoho Desk combines **daily API credits** with **concurrency** caps (not a single simple “N requests/min” for all cases).

### 5.1 Daily API credits

- Credits **reset every 24 hours** in the **data center timezone** (00:00–23:59:59).
- **Base + per-user** credits depend on **edition** and licensed users (excluding light agents per published tables).
- **Variable consumption** for list/search: cost can depend on **how “deep”** into the result set you fetch (e.g. high `from` indices can cost more credits than early pages).
- **Fixed** cost examples: **1 credit** for fetching a single record by ID.

### 5.2 Concurrency limits (simultaneous active calls)

| Edition (examples) | Concurrent calls (from published table) |
|--------------------|----------------------------------------|
| Free / Trial | 5 |
| Express / Standard | 10 |
| Professional | 15 |
| Enterprise / Zoho One / CRM Plus | 25 |

Exceeding concurrency → **`TOO_MANY_REQUESTS`** / HTTP **429** (see errors below).

### 5.3 Response headers (credit tracking)

| Header | Meaning |
|--------|---------|
| `X-Rate-Limit-Request-Weight-v3` | Credits consumed by **this** call |
| `X-Rate-Limit-Remaining-v3` | Credits **remaining** for the portal for the **current day** |
| `Retry-After` | Seconds to wait — appears when hitting **daily** credit exhaustion (documented behavior) |

### 5.4 Connector behavior

- On **429** or credit-related errors: **exponential backoff**, respect **`Retry-After`** when present, and **serialize** requests to stay under **concurrency** for the portal’s edition.
- Monitor **`X-Rate-Limit-Remaining-v3`** to throttle **before** hard failures.

---

## 6. Major entities and endpoints

Below: **representative** v1 paths under `.../api/v1/`. Paths are relative to the regional **`https://desk.zoho.<tld>/api/v1`** base.

### 6.1 Organizations (portals)

| Purpose | Method / path |
|--------|------------------|
| List orgs user can access | `GET /organizations` |
| Get org | `GET /organizations/{organization_Id}` |
| Accessible orgs (context) | `GET /accessibleOrganizations` |

Use org list + **`orgId`** for all subsequent calls.

### 6.2 Tickets

| Purpose | Method / path |
|--------|------------------|
| List tickets | `GET /tickets` |
| Get ticket | `GET /tickets/{ticket_id}` |
| Archived tickets | `GET /tickets/archivedTickets` |
| Threads / conversations | `GET /tickets/{ticket_id}/threads`, `.../threads/{thread_id}`, `.../conversations`, etc. |
| Attachments | `GET /tickets/{ticket_id}/attachments` |
| Ticket count | `GET /ticketsCount` |

### 6.3 Contacts and accounts

| Purpose | Method / path |
|--------|------------------|
| Accounts list (paginated) | `GET /accounts` |
| Contacts | `GET /contacts` / `GET /contacts/{id}` (full list in official API catalog) |
| Contact photo (example in docs) | `GET /contacts/{id}/photo` |

**Note:** Field sets for tickets, contacts, accounts, tasks can be **dynamic** (profile/security field filtering) — treat schema as **dynamic** at query time where possible.

### 6.4 Departments and agents

| Purpose | Method / path |
|--------|------------------|
| Departments | `GET /departments`, `GET /departments/{department_id}` |
| Department agents | `GET /departments/{department_id}/agents` |
| Department count | `GET /departments/count` |
| Agents | `GET /agents`, `GET /agents/{agent_id}` |
| Agent count | `GET /agents/count` |
| Agent by email | `GET /agents/email/{email}` |

### 6.5 Other notable areas

| Area | Examples |
|------|----------|
| Tasks | `/tasks` APIs (scopes `Desk.tasks.*`) |
| Articles (KB) | `/articles` (scopes `Desk.articles.*`) |
| Search | Search APIs (`Desk.search.READ`) |
| Webhooks | Outbound events for ticket/contact/account changes (separate webhook doc) |

### 6.6 OvalEdge object mapping (suggested)

| Desk concept | Suggested ObjectKind |
|--------------|----------------------|
| Organization | **CONTAINER** (portal) |
| Ticket, Contact, Account, Agent, Department, Task, Article | **ENTITY** |
| Saved search / report-like exports | **REPORT** only if modeled as exportable artifact (most lists are **ENTITY**) |

---

## 7. Error handling and HTTP status codes

### 7.1 HTTP status codes (common set from docs)

| Code | Typical meaning |
|------|-----------------|
| 200 | OK |
| 201 | Created |
| 204 | No content |
| 400 | Bad request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not found |
| 405 | Method not allowed |
| 413 | Payload too large |
| 415 | Unsupported media type |
| 422 | Unprocessable entity |
| **429** | **Too many requests** (credits / concurrency / throttling) |
| 500 | Internal server error |

### 7.2 Machine-readable `errorCode` values (examples)

| errorCode | When |
|-----------|------|
| `UNAUTHORIZED` | Invalid auth token |
| `INVALID_OAUTH` | OAuth token invalid or **expired** → refresh |
| `SCOPE_MISMATCH` | Missing scope |
| `OAUTH_ORG_MISMATCH` | **`orgId`** / org choice doesn’t match token-bound org |
| `FORBIDDEN` | Insufficient permission |
| `LICENSE_ACCESS_LIMITED` | Edition / license restriction (`feature`, `editionType`) |
| `URL_NOT_FOUND` | Bad URL |
| `METHOD_NOT_ALLOWED` | Wrong HTTP method |
| `RESOURCE_SIZE_EXCEEDED` | Payload too large |
| `UNSUPPORTED_MEDIA_TYPE` | Wrong content type |
| `INVALID_DATA` | Validation — check `errorType`: `invalid`, `duplicate`, `missing`; `fieldName` may use JSON Pointer |
| `UNPROCESSABLE_ENTITY` | Business rule failure |
| **`THRESHOLD_EXCEEDED`** | Too many requests in time window (**rate / credit threshold**) |
| **`TOO_MANY_REQUESTS`** | **Concurrency** exceeded |
| `INTERNAL_SERVER_ERROR` | Server-side failure |

**Practice:** Prefer **`errorCode`** + HTTP status for branching; surface **`message`** and optional **`fieldName`** to operators.

---

## 8. Connection validation endpoints

| Goal | Suggested call |
|------|----------------|
| Token valid | `GET /organizations` or `GET /accessibleOrganizations` |
| Org + token aligned | Any simple `GET` with **`orgId`** set (e.g. `GET /departments?limit=1`) |

---

## 9. SDK / client libraries

| Option | Notes |
|--------|------|
| **REST** | Fully supported; all integrations ultimately use HTTPS + JSON. |
| **Official Java SDK** | Documented Maven: **`com.zoho.desk:zohodesksdk`** from repository **`https://maven.zohodl.com`** (version — check Zoho’s current release on [Maven guide](https://www.zoho.com/desk/developers/javasdk/maven.html)). |
| **JDBC** | Not applicable. |
| **Sample OAuth (Java)** | [ZohoDeskOAuth](https://github.com/zohodesk-developers/ZohoDeskOAuth) |

---

## 10. Connection attributes (draft for OvalEdge UI)

| Key | Label | Type | Required | Secret | Notes |
|-----|-------|------|----------|--------|-------|
| `ZOHO_DESK_BASE_URL` | Desk API base URL | TEXT | Yes | No | e.g. `https://desk.zoho.com/api/v1` |
| `ZOHO_ACCOUNTS_URL` | Zoho Accounts URL | TEXT | Yes | No | e.g. `https://accounts.zoho.com` — must match DC |
| `ZOHO_ORG_ID` | Organization ID | TEXT | Yes | No | Portal orgId for `orgId` header |
| `ZOHO_CLIENT_ID` | OAuth Client ID | TEXT | Yes | No | From API Console |
| `ZOHO_CLIENT_SECRET` | OAuth Client Secret | PASSWORD | Yes | **Yes** | |
| `ZOHO_REFRESH_TOKEN` | Refresh Token | PASSWORD | Yes* | **Yes** | *If using stored refresh flow |
| `ZOHO_ACCESS_TOKEN` | Access Token (cached) | PASSWORD | Optional | **Yes** | Short-lived; refresh job updates |

---

## 11. Special considerations

- **Dynamic fields** on tickets/contacts/accounts/tasks — discovery via sample records or metadata endpoints where exposed; avoid hard-coded column lists.
- **Multi–data center:** OAuth callback may return **`accounts-server`** / **`location`** — persist and use for token refresh and Desk host selection.
- **Webhook** option exists for change notification (out of band from REST crawl).
- **Credit model** is not “flat RPS”; design **pagination** and **bulk** carefully to avoid burning daily credits.

---

## 12. Recommended implementation approach

1. **Auth:** OAuth **authorization code** or **Self Client** + **offline** refresh; store refresh token securely; refresh access token before **1 hour** expiry (or on `INVALID_OAUTH`).
2. **Transport:** Prefer **direct REST** in CSP for control and fewer external repos; optional **Java SDK** if team wants Zoho-maintained wrappers.
3. **Pagination:** `from` + `limit`, cap `limit` at **50** unless endpoint allows more.
4. **Throttling:** Honor **429**, **`Retry-After`**, **`X-Rate-Limit-*`**, and **concurrency** limits.
5. **Risks:** Org mismatch, DC mismatch, edition limits, dynamic schemas, credit exhaustion on deep pagination.

---

## 13. Summary table

| Aspect | Details |
|--------|---------|
| **Product category** | SaaS customer support / help desk |
| **Auth type(s)** | OAuth 2.0 (authorization code; refresh token with `access_type=offline`) |
| **API protocol** | REST (JSON), HTTPS |
| **Container type(s)** | Organization (Desk portal) |
| **Object types** | Tickets, Contacts, Accounts, Departments, Agents, Tasks, Articles, … |
| **Field discovery** | **Dynamic** (especially tickets/contacts/accounts) |
| **Pagination** | **`from`** (1-based) + **`limit`** (offset-style; max ~50 typical) |
| **Java SDK** | Yes — `com.zoho.desk:zohodesksdk` (Zoho Maven repo `maven.zohodl.com`) |
| **JDBC** | No |
| **Rate limits** | **Daily credits** + **concurrency** per edition; **`THRESHOLD_EXCEEDED`** / **`TOO_MANY_REQUESTS`**; headers **`X-Rate-Limit-*`**, **`Retry-After`** |
| **API docs URL** | [https://desk.zoho.com/DeskAPIDocument](https://desk.zoho.com/DeskAPIDocument) |

---

*This document was compiled from Zoho Desk’s published API guides (authentication, pagination, API credits/concurrency, error codes, and endpoint catalog). Confirm endpoint-specific **`limit`** caps and regional hostnames against the live documentation for your data center before production deployment.*
