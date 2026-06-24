package com.ovaledge.csp.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Legacy OvalEdge platform server types that must not be reused by SDK connectors.
 *
 * <p>Entries in {@code legacy-platform-server-types.txt} are kept exactly as stored on the platform
 * (spaces, hyphens, underscores, etc.). Matching for new SDK connector ids uses
 * {@link ServerTypeNormalizer#normalize(String)} so {@code qliksense} is blocked when the list contains
 * {@code qlik sense} or {@code quickbooks-online}.
 *
 * <p>In-repo SDK modules contribute dynamically discovered server types via
 * {@link SdkConnectorReactorScanner}; new connectors are blocked from names on the legacy txt list or already
 * owned in the reactor ({@link #blockedNamesForNewConnector}).
 */
public final class LegacyPlatformServerTypes {

    /**
     * Classpath resource under {@code csp-api/src/main/resources/}; single source of truth for legacy platform
     * reserved types (one platform {@code server} value per line, {@code #} comments allowed).
     */
    private static final String FORBIDDEN_RESOURCE = "legacy-platform-server-types.txt";

    /** Legacy platform types as written in the txt list (trimmed only). */
    private static final Set<String> FORBIDDEN_RAW = Collections.unmodifiableSet(loadRawResourceSet(FORBIDDEN_RESOURCE));

    /** Normalized legacy types used for equality checks against new connector ids. */
    private static final Set<String> FORBIDDEN_NORMALIZED = Collections.unmodifiableSet(normalizeAll(FORBIDDEN_RAW));

    private LegacyPlatformServerTypes() {}

    /**
     * Returns legacy platform server types exactly as listed in the txt resource (trimmed per line).
     *
     * @return unmodifiable set of platform {@code server} identifiers from the legacy list
     */
    public static Set<String> forbiddenTypes() {
        return FORBIDDEN_RAW;
    }

    /**
     * Returns whether the value matches a legacy reserved server type after normalization.
     *
     * @param serverType raw platform name, legacy list entry, or new connector id
     * @return {@code true} if reserved after canonical normalization
     */
    public static boolean isForbidden(String serverType) {
        if (serverType == null || serverType.isEmpty()) {
            return false;
        }
        return FORBIDDEN_NORMALIZED.contains(normalize(serverType));
    }

    /**
     * Returns whether the type is reserved by the legacy txt list and not owned by any in-repo SDK module.
     *
     * @param serverType raw or normalized server type
     * @param ownedServerTypes union of server types from reactor SDK modules (config + runtime)
     * @return {@code true} when forbidden and not in the owned set
     */
    public static boolean violatesSdkGate(String serverType, Set<String> ownedServerTypes) {
        if (serverType == null || serverType.isEmpty() || ownedServerTypes == null) {
            return false;
        }
        String normalized = normalize(serverType);
        return FORBIDDEN_NORMALIZED.contains(normalized) && !ownedServerTypes.contains(normalized);
    }

    /**
     * Returns server type names blocked for a new connector: legacy txt list (as-is) union in-repo owned types.
     *
     * <p>Displayed in the Connector Generator reserved-names UI. Compare proposed ids with
     * {@link ServerTypeNormalizer#normalize(String)} — not raw string equality.
     *
     * @param repoRoot directory containing root {@code pom.xml}
     * @return sorted unmodifiable set of blocked display names
     */
    public static Set<String> blockedNamesForNewConnector(Path repoRoot) {
        try {
            Set<String> blocked = new TreeSet<>(FORBIDDEN_RAW);
            blocked.addAll(SdkConnectorReactorScanner.scan(repoRoot).ownedServerTypes());
            return Collections.unmodifiableSet(blocked);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to scan SDK connector server types under " + repoRoot, ex);
        }
    }

    /**
     * Returns whether a proposed connector name is blocked (legacy txt or already used in-repo).
     *
     * <p>Uses normalized equality against legacy ∪ in-repo reserved types.
     *
     * @param name connector name or artifact id
     * @param repoRoot repository root for reactor scan
     * @return {@code true} when the normalized name must not be used for a new connector
     */
    public static boolean isBlockedForNewConnector(String name, Path repoRoot) {
        return isExactBlockedForNewConnector(name, repoRoot);
    }

    /**
     * Normalized match against legacy txt ∪ in-repo owned types.
     */
    public static boolean isExactBlockedForNewConnector(String name, Path repoRoot) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return false;
        }
        return blockedNormalizedNamesForNewConnector(repoRoot).contains(normalized);
    }

    /**
     * User-facing message when a server type is reserved by the legacy platform.
     *
     * @param serverType identifier shown in the message (typically the normalized artifact id)
     * @return validation message
     */
    public static String forbiddenMessage(String serverType) {
        return "serverType \"" + serverType
                + "\" is reserved by the legacy OvalEdge platform. Choose a different identifier.";
    }

    /**
     * Next available versioned alternate for a blocked connector name ({@code stemv2}, {@code stemv3}, …).
     */
    public static String suggestAlternateForNewConnector(String name, Path repoRoot) {
        String normalized = normalize(name);
        return ServerTypeNormalizer.suggestAlternate(normalized, blockedNormalizedNamesForNewConnector(repoRoot));
    }

    /**
     * User-facing message for the Connector Generator when the proposed name is blocked.
     *
     * @param serverType normalized artifact id from the connector name
     * @param repoRoot repository root used to classify legacy vs in-repo duplicate
     * @return validation message including a versioned naming suggestion
     */
    public static String generatorBlockedMessage(String serverType, Path repoRoot) {
        String normalized = normalize(serverType);
        if (isForbidden(normalized) && !isOwnedInRepo(normalized, repoRoot)) {
            return "Connector Name resolves to server type \"" + normalized
                    + "\" which is reserved by the legacy OvalEdge platform (already used by built-in connectors)."
                    + " Choose a different name (for example: \""
                    + suggestAlternateForNewConnector(normalized, repoRoot) + "\").";
        }
        return "Connector Name resolves to server type \"" + normalized
                + "\" which is already used by an SDK connector in this repository."
                + " Choose a different name (for example: \""
                + suggestAlternateForNewConnector(normalized, repoRoot) + "\").";
    }

    /**
     * Whether an in-repo SDK module already claims this canonical server type (config or runtime).
     * Used to distinguish legacy txt conflicts from duplicate in-repo names in generator messages.
     */
    private static boolean isOwnedInRepo(String normalizedServerType, Path repoRoot) {
        try {
            return SdkConnectorReactorScanner.scan(repoRoot).ownedServerTypes().contains(normalizedServerType);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to scan SDK connector server types under " + repoRoot, ex);
        }
    }

    private static Set<String> blockedNormalizedNamesForNewConnector(Path repoRoot) {
        try {
            Set<String> blocked = new TreeSet<>(FORBIDDEN_NORMALIZED);
            blocked.addAll(SdkConnectorReactorScanner.scan(repoRoot).ownedServerTypes());
            return Collections.unmodifiableSet(blocked);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to scan SDK connector server types under " + repoRoot, ex);
        }
    }

    private static String normalize(String serverType) {
        return ServerTypeNormalizer.normalize(serverType);
    }

    private static Set<String> normalizeAll(Set<String> rawValues) {
        Set<String> normalized = new TreeSet<>();
        for (String raw : rawValues) {
            String canonical = normalize(raw);
            if (!canonical.isEmpty()) {
                normalized.add(canonical);
            }
        }
        return normalized;
    }

    /** Loads line-oriented entries from a classpath resource without altering platform spelling. */
    private static Set<String> loadRawResourceSet(String resourceName) {
        ClassLoader loader = LegacyPlatformServerTypes.class.getClassLoader();
        InputStream in = loader.getResourceAsStream(resourceName);
        if (in == null) {
            throw new IllegalStateException("Missing classpath resource: " + resourceName);
        }
        Set<String> values = new TreeSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                values.add(trimmed);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + resourceName, e);
        }
        return values;
    }
}
