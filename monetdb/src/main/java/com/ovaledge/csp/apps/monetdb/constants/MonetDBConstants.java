package com.ovaledge.csp.apps.monetdb.constants;

/**
 * MonetDB JDBC connector constants: server type, connection attribute keys,
 * labels, descriptions, JDBC URL format, and driver class.
 */
public final class MonetDBConstants {

    // ===== SERVER TYPE =====
    public static final String SERVER_TYPE = "monetdb";

    // ===== CONNECTION ATTRIBUTE KEYS =====
    public static final String KEY_HOST = "MONETDB_HOST";
    public static final String KEY_PORT = "MONETDB_PORT";
    public static final String KEY_DATABASE = "MONETDB_DATABASE";
    public static final String KEY_USERNAME = "MONETDB_USERNAME";
    public static final String KEY_PASSWORD = "MONETDB_PASSWORD";

    // ===== LABELS =====
    public static final String LABEL_HOST = "Host";
    public static final String LABEL_PORT = "Port";
    public static final String LABEL_DATABASE = "Database";
    public static final String LABEL_USERNAME = "Username";
    public static final String LABEL_PASSWORD = "Password";

    // ===== DESCRIPTIONS =====
    public static final String DESC_HOST = "MonetDB server host name or IP address";
    public static final String DESC_PORT = "MonetDB server port (default 50000)";
    public static final String DESC_DATABASE = "Database name";
    public static final String DESC_USERNAME = "Database user name";
    public static final String DESC_PASSWORD = "Database password";

    // ===== JDBC =====
    public static final String JDBC_DRIVER_CLASS = "org.monetdb.jdbc.MonetDriver";
    public static final String JDBC_URL_PREFIX = "jdbc:monetdb://";
    public static final int DEFAULT_PORT = 50000;

    // ===== OBJECT TYPES (for getObjects filter when entityType = ENTITY) =====
    /** Filter key to distinguish object subtypes when multiple map to ObjectKind.ENTITY */
    public static final String OBJECT_SUBTYPE_TABLE = "table";
    public static final String OBJECT_SUBTYPE_VIEW = "view";
    /** Display names matching getSupportedObjects(); used if filter uses displayName */
    public static final String DISPLAY_NAME_TABLES = "Tables";
    public static final String DISPLAY_NAME_VIEWS = "Views";
    public static final String DISPLAY_NAME_FUNCTIONS = "Functions";
    public static final String DISPLAY_NAME_SEQUENCES = "Sequences";
    public static final String DISPLAY_NAME_INDEXES = "Indexes";
    public static final String DISPLAY_NAME_TRIGGERS = "Triggers";
    /** Prefixes for entityId so getFields() can dispatch (table/view have no prefix) */
    public static final String ENTITY_ID_PREFIX_FUNCTION = "func:";
    public static final String ENTITY_ID_PREFIX_SEQUENCE = "seq:";
    public static final String ENTITY_ID_PREFIX_INDEX = "idx:";
    public static final String ENTITY_ID_PREFIX_TRIGGER = "trg:";

    // ===== VALIDATION MESSAGES =====
    public static final String MSG_SUCCESS = "Connection successful.";
    public static final String MSG_MISSING_HOST = "Host is required.";
    public static final String MSG_MISSING_DATABASE = "Database is required.";
    public static final String MSG_INVALID_PORT = "Port must be a valid number.";

    private MonetDBConstants() {
        throw new UnsupportedOperationException("Constants class");
    }
}
