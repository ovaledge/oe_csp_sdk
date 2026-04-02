package com.ovaledge.csp.apps.awsconsole.constants;

/**
 * AWS Console connector constants: server type, connection attribute keys,
 * labels, descriptions, and supported object display names.
 */
public final class AwsConsoleConstants {

    // ===== SERVER TYPE =====
    public static final String SERVER_TYPE = "awsconsole";

    // ===== CONNECTION ATTRIBUTE KEYS =====
    public static final String KEY_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    public static final String KEY_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    public static final String KEY_REGION = "AWS_REGION";

    // ===== LABELS =====
    public static final String LABEL_ACCESS_KEY_ID = "Access Key ID";
    public static final String LABEL_SECRET_ACCESS_KEY = "Secret Access Key";
    public static final String LABEL_REGION = "Region";

    // ===== DESCRIPTIONS =====
    public static final String DESC_ACCESS_KEY_ID = "AWS IAM access key ID";
    public static final String DESC_SECRET_ACCESS_KEY = "AWS IAM secret access key";
    public static final String DESC_REGION = "AWS region (e.g. us-east-1)";

    // ===== DEFAULT REGION =====
    public static final String DEFAULT_REGION = "us-east-1";

    // ===== SUPPORTED OBJECTS (entity types) =====
    /** Display name for entity type: EC2 Instances */
    public static final String DISPLAY_NAME_EC2_INSTANCES = "EC2 Instances";
    /** Display name for entity type: S3 Buckets (exposed as ENTITY for listing) */
    public static final String DISPLAY_NAME_S3_BUCKETS = "S3 Buckets";
    /** Filter key to distinguish object subtypes when multiple map to ObjectKind.ENTITY */
    public static final String FILTER_OBJECT_SUBTYPE = "objectSubType";
    public static final String OBJECT_SUBTYPE_EC2_INSTANCES = "ec2_instances";
    public static final String OBJECT_SUBTYPE_S3_BUCKETS = "s3_buckets";

    // ===== VALIDATION MESSAGES =====
    public static final String MSG_SUCCESS = "Connection successful.";
    public static final String MSG_MISSING_ACCESS_KEY = "Access Key ID is required.";
    public static final String MSG_MISSING_SECRET_KEY = "Secret Access Key is required.";

    private AwsConsoleConstants() {
        throw new UnsupportedOperationException("Constants class");
    }
}
