package com.ovaledge.csp.apps.${artifactId}.constants;

public class ${classPrefix}Constants {

    public static final String SERVER_TYPE = "${artifactId}";

    private ${classPrefix}Constants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /*
     * Add connector-specific constants here.
     *
     * Typical entries:
     * - public static final String SERVER_TYPE = "salesforce"; // lowercase identifier used at runtime
     * - public static final String DEFAULT_PORT = "443";
     *     (use descriptive UPPER_SNAKE_CASE names)
     *
     * Use these constants from your connector classes instead of hardcoding strings.
     * Keep keys stable because they are referenced by configuration, UI and tests.
     */
}
