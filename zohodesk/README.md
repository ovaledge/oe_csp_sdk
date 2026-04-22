# Zoho Desk Connector (CSP SDK)

Apps connector for **Zoho Desk REST API** using `ConnectionPoolManager` REST resources and OAuth2 refresh-token authentication.

- **Connector name:** Zoho Desk
- **Server type:** `zohodesk`
- **Data source:** Zoho Desk REST API (`/api/v1`)

## Authentication

This implementation supports **refresh-token OAuth**.

Required attributes:
- `ZOHO_DESK_BASE_URL` (for example `https://desk.zoho.com/api/v1`)
- `ZOHO_ACCOUNTS_URL` (for example `https://accounts.zoho.com`)
- `ZOHO_ORG_ID`
- `ZOHO_CLIENT_ID`
- `ZOHO_CLIENT_SECRET`
- `ZOHO_REFRESH_TOKEN`

Optional:
- `ZOHO_SCOPE`
- `ZOHO_ACCESS_TOKEN` (cached/hidden)

## Metadata scope (v1)

- Containers: **Organizations**
- Entity objects:
  - Tickets
  - Contacts
  - Accounts
  - Departments
  - Agents

Field discovery uses dynamic sample payload introspection from each entity API.

## Query behavior

- `fetchData` supports the same core 5 entities.
- Pagination maps CSP `offset`/`limit` to Zoho `from`/`limit`.
- Count endpoints used when available:
  - `/ticketsCount`
  - `/departments/count`
  - `/agents/count`

## Rate limiting and retries

- Retries are applied for `429` and transient `5xx` responses.
- `Retry-After` header is honored when present.
- Exponential backoff is bounded.
- Connection pool REST resource is evicted on `401` auth failures.

## Quick app endpoints

Base path: `/api/zohodesk`

- `GET /health`
- `GET /supported-objects`
- `POST /connection/validate`

## Build and test

From repository root:

```bash
mvn -pl zohodesk -am test
mvn -pl zohodesk -am package
```
