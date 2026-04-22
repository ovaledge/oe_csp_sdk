package com.ovaledge.csp.apps.zohodesk.constants;

public class ZohodeskConstants {

    public static final String SERVER_TYPE = "zohodesk";
    public static final String API_VERSION_PATH = "/api/v1";
    public static final String DEFAULT_BASE_URL = "https://desk.zoho.com/api/v1";
    public static final String DEFAULT_ACCOUNTS_URL = "https://accounts.zoho.com";
    public static final String DEFAULT_TOKEN_ENDPOINT = "/oauth/v2/token";
    public static final String DEFAULT_SCOPE = "Desk.basic.READ,Desk.tickets.READ,Desk.contacts.READ";
    public static final String DEFAULT_GRANT_TYPE = "refresh_token";

    public static final String KEY_BASE_URL = "ZOHO_DESK_BASE_URL";
    public static final String KEY_ACCOUNTS_URL = "ZOHO_ACCOUNTS_URL";
    public static final String KEY_ORG_ID = "ZOHO_ORG_ID";
    public static final String KEY_CLIENT_ID = "ZOHO_CLIENT_ID";
    public static final String KEY_CLIENT_SECRET = "ZOHO_CLIENT_SECRET";
    public static final String KEY_REFRESH_TOKEN = "ZOHO_REFRESH_TOKEN";
    public static final String KEY_ACCESS_TOKEN = "ZOHO_ACCESS_TOKEN";
    public static final String KEY_SCOPE = "ZOHO_SCOPE";

    public static final String LABEL_BASE_URL = "Desk API Base URL";
    public static final String LABEL_ACCOUNTS_URL = "Accounts URL";
    public static final String LABEL_ORG_ID = "Organization ID";
    public static final String LABEL_CLIENT_ID = "Client ID";
    public static final String LABEL_CLIENT_SECRET = "Client Secret";
    public static final String LABEL_REFRESH_TOKEN = "Refresh Token";
    public static final String LABEL_ACCESS_TOKEN = "Access Token";
    public static final String LABEL_SCOPE = "OAuth Scopes";

    public static final String DESC_BASE_URL = "Regional Zoho Desk API URL, for example https://desk.zoho.com/api/v1";
    public static final String DESC_ACCOUNTS_URL = "Regional Zoho accounts host, for example https://accounts.zoho.com";
    public static final String DESC_ORG_ID = "Zoho Desk organization id used in the orgId header";
    public static final String DESC_CLIENT_ID = "OAuth client identifier created in Zoho API Console";
    public static final String DESC_CLIENT_SECRET = "OAuth client secret created in Zoho API Console";
    public static final String DESC_REFRESH_TOKEN = "Refresh token generated with offline access";
    public static final String DESC_ACCESS_TOKEN = "Optional pre-fetched access token";
    public static final String DESC_SCOPE = "Comma-separated scopes used by token refresh flow";

    public static final String FILTER_OBJECT_SUBTYPE = "objectSubType";
    public static final String OBJECT_SUBTYPE_TICKETS = "tickets";
    public static final String OBJECT_SUBTYPE_CONTACTS = "contacts";
    public static final String OBJECT_SUBTYPE_ACCOUNTS = "accounts";
    public static final String OBJECT_SUBTYPE_DEPARTMENTS = "departments";
    public static final String OBJECT_SUBTYPE_AGENTS = "agents";
    public static final String OBJECT_SUBTYPE_REPORTS = "reports";

    public static final String DISPLAY_NAME_TICKETS = "Tickets";
    public static final String DISPLAY_NAME_CONTACTS = "Contacts";
    public static final String DISPLAY_NAME_ACCOUNTS = "Accounts";
    public static final String DISPLAY_NAME_DEPARTMENTS = "Departments";
    public static final String DISPLAY_NAME_AGENTS = "Agents";
    public static final String DISPLAY_NAME_REPORTS = "Reports";

    public static final String ENDPOINT_ORGANIZATIONS = "/organizations";
    public static final String ENDPOINT_ACCESSIBLE_ORGANIZATIONS = "/accessibleOrganizations";
    public static final String ENDPOINT_TICKETS = "/tickets";
    public static final String ENDPOINT_TICKETS_COUNT = "/ticketsCount";
    public static final String ENDPOINT_CONTACTS = "/contacts";
    public static final String ENDPOINT_ACCOUNTS = "/accounts";
    public static final String ENDPOINT_DEPARTMENTS = "/departments";
    public static final String ENDPOINT_DEPARTMENTS_COUNT = "/departments/count";
    public static final String ENDPOINT_AGENTS = "/agents";
    public static final String ENDPOINT_AGENTS_COUNT = "/agents/count";
    public static final String ENDPOINT_REPORTS = "/reports";

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MIN_PAGE_SIZE = 1;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_RETRIES = 3;
    public static final long RETRY_BASE_MS = 1000L;
    public static final long RETRY_MAX_MS = 10000L;

    public static final String MSG_SUCCESS = "Zoho Desk connection validated successfully.";
    public static final String MSG_MISSING_BASE_URL = "Desk API base URL is required.";
    public static final String MSG_MISSING_ACCOUNTS_URL = "Zoho Accounts URL is required.";
    public static final String MSG_MISSING_ORG_ID = "Organization ID is required.";
    public static final String MSG_MISSING_CLIENT_ID = "Client ID is required.";
    public static final String MSG_MISSING_CLIENT_SECRET = "Client secret is required.";
    public static final String MSG_MISSING_REFRESH_TOKEN = "Refresh token is required.";

    private ZohodeskConstants() {
        throw new UnsupportedOperationException("Utility class");
    }
}
