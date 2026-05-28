package com.ovaledge.csp.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Legacy OvalEdge platform server types that must not be reused by SDK connectors.
 *
 * <p>Values are normalized to the same canonical form used by connector artifact/server type generation
 * ({@link ServerTypeNormalizer}). In-repo SDK modules contribute dynamically discovered server types via
 * {@link SdkConnectorReactorScanner}; new connectors are blocked from names on the legacy txt list or already
 * owned in the reactor ({@link #blockedNamesForNewConnector}).
 */
public final class LegacyPlatformServerTypes {

    /**
     * Classpath resource under {@code csp-api/src/main/resources/}; single source of truth for legacy platform
     * reserved types (one normalized id per line, {@code #} comments allowed).
     */
    private static final String FORBIDDEN_RESOURCE = "legacy-platform-server-types.txt";

    /** Normalized legacy platform types loaded once at class initialization. */
    private static final Set<String> FORBIDDEN = Collections.unmodifiableSet(loadResourceSet(FORBIDDEN_RESOURCE));

    private LegacyPlatformServerTypes() {}

    /**
     * Returns the full set of legacy platform server types (canonical form) from the txt list only.
     *
     * @return unmodifiable set of normalized reserved identifiers
     */
    public static Set<String> forbiddenTypes() {
        return FORBIDDEN;
    }

    /**
     * Returns whether the normalized value matches a legacy reserved server type (txt list).
     *
     * @param serverType raw or normalized server type / artifact id
     * @return {@code true} if reserved after canonical normalization
     */
    public static boolean isForbidden(String serverType) {
        if (serverType == null || serverType.isEmpty()) {
            return false;
        }
        return FORBIDDEN.contains(normalize(serverType));
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
        return FORBIDDEN.contains(normalized) && !ownedServerTypes.contains(normalized);
    }

    /**
     * Returns server type names blocked for a new connector: legacy txt list union in-repo owned types.
     *
     * @param repoRoot directory containing root {@code pom.xml}
     * @return sorted unmodifiable set of blocked canonical names
     */
    public static Set<String> blockedNamesForNewConnector(Path repoRoot) {
        try {
            Set<String> blocked = new TreeSet<>(FORBIDDEN);
            blocked.addAll(SdkConnectorReactorScanner.scan(repoRoot).ownedServerTypes());
            return Collections.unmodifiableSet(blocked);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to scan SDK connector server types under " + repoRoot, ex);
        }
    }

    /**
     * Returns whether a proposed connector name is blocked (legacy txt or already used in-repo).
     *
     * <p>Blocks on exact canonical {@code serverType}, or when the Java package (hyphens removed from
     * artifact id) would match an existing in-repo SDK module — that causes classpath/SPI ambiguity.
     *
     * @param name connector name or artifact id
     * @param repoRoot repository root for reactor scan
     * @return {@code true} when the normalized name must not be used for a new connector
     */
    public static boolean isBlockedForNewConnector(String name, Path repoRoot) {
        return isExactBlockedForNewConnector(name, repoRoot) || hasInRepoPackageConflict(name, repoRoot);
    }

    /**
     * Exact canonical match against legacy txt ∪ in-repo owned types.
     */
    public static boolean isExactBlockedForNewConnector(String name, Path repoRoot) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return false;
        }
        return blockedNamesForNewConnector(repoRoot).contains(normalized);
    }

    /**
     * Whether {@code artifactId.replace("-", "")} matches an existing SDK module's Java package / serverType
     * (e.g. {@code "Monet DB"} and {@code monetdb} both normalize to {@code monetdb}).
     */
    public static boolean hasInRepoPackageConflict(String name, Path repoRoot) {
        try {
            String normalized = normalize(name);
            String compactName = compact(name);
            if (compactName.isEmpty()) {
                return false;
            }
            for (String ownedType : SdkConnectorReactorScanner.scan(repoRoot).ownedServerTypes()) {
                if (!ownedType.equals(normalized) && compact(ownedType).equals(compactName)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to scan SDK connector server types under " + repoRoot, ex);
        }
    }

    /**
     * In-repo server types with the same compact Java package as the proposed name (excluding exact match).
     */
    public static List<String> inRepoPackageConflictTypes(String name, Path repoRoot) {
        if (name == null || name.isEmpty()) {
            return List.of();
        }
        String normalized = normalize(name);
        String compactName = compact(name);
        if (compactName.isEmpty()) {
            return List.of();
        }
        List<String> conflicts = new ArrayList<>();
        try {
            for (String ownedType : SdkConnectorReactorScanner.scan(repoRoot).ownedServerTypes()) {
                if (!ownedType.equals(normalized) && compact(ownedType).equals(compactName)) {
                    conflicts.add(ownedType);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to scan SDK connector server types under " + repoRoot, ex);
        }
        Collections.sort(conflicts);
        return conflicts;
    }

    /**
     * Reserved names similar by compact form but not blocked (typically legacy txt only).
     */
    public static List<String> similarBlockedNames(String name, Path repoRoot) {
        if (name == null || name.isEmpty() || isBlockedForNewConnector(name, repoRoot)) {
            return List.of();
        }
        String normalized = normalize(name);
        String compactName = compact(name);
        if (compactName.isEmpty()) {
            return List.of();
        }
        List<String> similar = new ArrayList<>();
        for (String blocked : blockedNamesForNewConnector(repoRoot)) {
            if (!blocked.equals(normalized) && compact(blocked).equals(compactName)) {
                similar.add(blocked);
            }
        }
        Collections.sort(similar);
        return similar;
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
     * Next available versioned alternate for a blocked connector name ({@code stem-v2}, {@code stem-v3}, …).
     */
    public static String suggestAlternateForNewConnector(String name, Path repoRoot) {
        String normalized = normalize(name);
        return ServerTypeNormalizer.suggestAlternate(normalized, blockedNamesForNewConnector(repoRoot));
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
        List<String> packageConflicts = inRepoPackageConflictTypes(serverType, repoRoot);
        if (!packageConflicts.isEmpty()) {
            String existing = packageConflicts.get(0);
            return "Connector Name resolves to module \"" + normalized
                    + "\" with Java package \"" + compact(serverType)
                    + "\", which matches the existing SDK module \"" + existing
                    + "\" (same package on the classpath and risk of SPI/class conflicts)."
                    + " Choose a different name (for example: \""
                    + suggestAlternateForNewConnector(normalized, repoRoot) + "\").";
        }
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

    private static String normalize(String serverType) {
        return ServerTypeNormalizer.normalize(serverType);
    }

    private static String compact(String serverType) {
        return ServerTypeNormalizer.compact(serverType);
    }

    /** Loads and normalizes line-oriented entries from a classpath resource. */
    private static Set<String> loadResourceSet(String resourceName) {
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
                values.add(normalize(trimmed));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + resourceName, e);
        }
        return values;
    }
}
