package com.ovaledge.csp.validation;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical normalization for connector artifact ids and legacy server type identifiers.
 *
 * <p>Only letters, digits, spaces, and hyphens are allowed in user input. Normalization lowercases,
 * strips other characters, collapses whitespace to hyphens, and dedupes hyphens (e.g. {@code "Qlik Sense"}
 * → {@code qlik-sense}).
 */
public final class ServerTypeNormalizer {

    private static final String DISALLOWED_CHAR_PATTERN = ".*[^a-zA-Z0-9\\s-].*";
    private static final Pattern VERSION_SUFFIX = Pattern.compile("-v(\\d+)$");
    private static final int MIN_SUGGESTED_VERSION = 2;
    private static final int MAX_SUGGESTION_SCAN = 10_000;

    private ServerTypeNormalizer() {}

    /**
     * Whether the raw value contains characters other than letters, digits, spaces, or hyphens.
     */
    public static boolean hasDisallowedCharacters(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return false;
        }
        return rawValue.matches(DISALLOWED_CHAR_PATTERN);
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
        String lower = rawValue.trim().toLowerCase(Locale.ROOT);
        String cleaned = lower.replaceAll("[^a-z0-9\\s-]", "");
        String hyphenated = cleaned.trim().replaceAll("\\s+", "-").replaceAll("-+", "-");
        return hyphenated.replaceAll("^-|-$", "");
    }

    /**
     * Compact form for Java package names and duplicate detection (hyphens removed).
     */
    public static String compact(String rawValue) {
        return normalize(rawValue).replace("-", "");
    }

    /**
     * Suggested alternate when no blocked-name set is available (defaults to {@code -v2}).
     */
    public static String suggestAlternate(String normalizedServerType) {
        return suggestAlternate(normalizedServerType, Set.of());
    }

    /**
     * Suggested alternate when a name is reserved: next {@code -vN} not in {@code blockedNames},
     * accounting for existing {@code stem-v2}, {@code stem-v3}, etc. and Java package (compact) clashes.
     *
     * @param normalizedServerType canonical artifact id (may already end with {@code -vN})
     * @param blockedNames legacy ∪ in-repo reserved server types (canonical form)
     */
    public static String suggestAlternate(String normalizedServerType, Set<String> blockedNames) {
        String stem = stripTrailingVersionSuffix(normalizedServerType);
        if (stem.isEmpty()) {
            stem = "connector";
        }
        int version = nextSuggestedVersion(stem, blockedNames);
        return firstAvailableVersionedName(stem, version, blockedNames);
    }

    static String stripTrailingVersionSuffix(String normalizedServerType) {
        if (normalizedServerType == null || normalizedServerType.isEmpty()) {
            return "";
        }
        Matcher matcher = VERSION_SUFFIX.matcher(normalizedServerType);
        if (matcher.find()) {
            return normalizedServerType.substring(0, matcher.start());
        }
        return normalizedServerType;
    }

    private static int nextSuggestedVersion(String stem, Set<String> blockedNames) {
        int maxVersion = 1;
        if (blockedNames == null || blockedNames.isEmpty()) {
            return MIN_SUGGESTED_VERSION;
        }
        String versionPrefix = stem + "-v";
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
            String candidate = stem + "-v" + version;
            if (!isTaken(candidate, blockedNames)) {
                return candidate;
            }
            version++;
        }
        return stem + "-v" + version;
    }

    private static boolean isTaken(String candidate, Set<String> blockedNames) {
        if (blockedNames == null || blockedNames.isEmpty()) {
            return false;
        }
        if (blockedNames.contains(candidate)) {
            return true;
        }
        String compactCandidate = compact(candidate);
        for (String blocked : blockedNames) {
            if (compact(blocked).equals(compactCandidate)) {
                return true;
            }
        }
        return false;
    }
}
