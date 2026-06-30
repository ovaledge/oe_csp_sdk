package com.ovaledge.csp.validation;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical normalization for connector artifact ids and legacy server type identifiers.
 *
 * <p>Canonical form is lowercase letters and digits only (no spaces or hyphens). Normalization
 * lowercases and strips all other characters (e.g. {@code "Qlik Sense"} → {@code qliksense},
 * {@code "quickbooks-online"} → {@code quickbooksonline}).
 *
 * <p>Generator input must already match {@link #isValidConnectorId(String)}; {@link #normalize(String)}
 * is also used when comparing legacy list entries and in-repo server types.
 */
public final class ServerTypeNormalizer {

    private static final String VALID_CONNECTOR_ID_PATTERN = "^[a-z][a-z0-9]*$";
    private static final Pattern VERSION_SUFFIX = Pattern.compile("v(\\d+)$");
    private static final int MIN_SUGGESTED_VERSION = 2;
    private static final int MAX_SUGGESTION_SCAN = 10_000;

    private ServerTypeNormalizer() {}

    /**
     * Whether the raw value is a valid connector id: lowercase letter first, then letters/digits only.
     */
    public static boolean isValidConnectorId(String rawValue) {
        return rawValue != null && !rawValue.isEmpty() && rawValue.matches(VALID_CONNECTOR_ID_PATTERN);
    }

    /**
     * Whether the raw value is not a valid connector id (for generator validation messages).
     */
    public static boolean hasDisallowedCharacters(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return false;
        }
        return !rawValue.matches(VALID_CONNECTOR_ID_PATTERN);
    }

    /**
     * Normalizes a raw connector name or server type to the canonical artifact id / serverType form.
     *
     * @param rawValue user input or list entry; {@code null} yields an empty string
     * @return normalized identifier, never {@code null}
     */
    public static String normalize(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * PascalCase prefix for generated Java types (capitalize first character of the canonical id).
     */
    public static String toPascalCase(String normalizedId) {
        if (normalizedId == null || normalizedId.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(normalizedId.charAt(0)) + normalizedId.substring(1);
    }

    /**
     * Suggested alternate when no blocked-name set is available (defaults to {@code v2} suffix).
     */
    public static String suggestAlternate(String normalizedServerType) {
        return suggestAlternate(normalizedServerType, Set.of());
    }

    /**
     * Suggested alternate when a name is reserved: next {@code vN} suffix not in {@code blockedNames},
     * accounting for existing {@code stemv2}, {@code stemv3}, etc.
     *
     * @param normalizedServerType canonical artifact id (may already end with {@code vN})
     * @param blockedNames legacy ∪ in-repo reserved server types (canonical form)
     */
    public static String suggestAlternate(String normalizedServerType, Set<String> blockedNames) {
        String stem = stripTrailingVersionSuffix(normalizedServerType, blockedNames);
        if (stem.isEmpty()) {
            stem = "connector";
        }
        int version = nextSuggestedVersion(stem, blockedNames);
        return firstAvailableVersionedName(stem, version, blockedNames);
    }

    /**
     * Strips a trailing {@code vN} version suffix only when {@code N >= 2} and the suffix is a known
     * versioned alternate (full name or stem is in {@code blockedNames}). Prevents treating legitimate
     * ids such as {@code archivev2} as versioned when they are not blocked.
     */
    static String stripTrailingVersionSuffix(String normalizedServerType, Set<String> blockedNames) {
        if (normalizedServerType == null || normalizedServerType.isEmpty()) {
            return "";
        }
        Matcher matcher = VERSION_SUFFIX.matcher(normalizedServerType);
        if (!matcher.find()) {
            return normalizedServerType;
        }
        int version = Integer.parseInt(matcher.group(1));
        if (version < MIN_SUGGESTED_VERSION) {
            return normalizedServerType;
        }
        String stem = normalizedServerType.substring(0, matcher.start());
        if (stem.isEmpty()) {
            return normalizedServerType;
        }
        if (blockedNames != null
                && (blockedNames.contains(normalizedServerType) || blockedNames.contains(stem))) {
            return stem;
        }
        return normalizedServerType;
    }

    private static int nextSuggestedVersion(String stem, Set<String> blockedNames) {
        int maxVersion = 1;
        if (blockedNames == null || blockedNames.isEmpty()) {
            return MIN_SUGGESTED_VERSION;
        }
        String versionPrefix = stem + "v";
        for (String blocked : blockedNames) {
            if (stem.equals(blocked)) {
                maxVersion = Math.max(maxVersion, 1);
            }
            if (blocked.startsWith(versionPrefix)) {
                String suffix = blocked.substring(versionPrefix.length());
                if (suffix.matches("\\d+")) {
                    maxVersion = Math.max(maxVersion, Integer.parseInt(suffix));
                }
            }
        }
        return Math.max(MIN_SUGGESTED_VERSION, maxVersion + 1);
    }

    private static String firstAvailableVersionedName(String stem, int startVersion, Set<String> blockedNames) {
        int version = Math.max(MIN_SUGGESTED_VERSION, startVersion);
        for (int attempts = 0; attempts < MAX_SUGGESTION_SCAN; attempts++) {
            String candidate = stem + "v" + version;
            if (!isTaken(candidate, blockedNames)) {
                return candidate;
            }
            version++;
        }
        return stem + "v" + version;
    }

    private static boolean isTaken(String candidate, Set<String> blockedNames) {
        if (blockedNames == null || blockedNames.isEmpty()) {
            return false;
        }
        return blockedNames.contains(candidate);
    }
}
