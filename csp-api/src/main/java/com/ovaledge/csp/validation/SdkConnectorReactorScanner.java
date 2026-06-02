package com.ovaledge.csp.validation;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

/**
 * Discovers SDK connector {@code serverType} values from the multi-module repository layout.
 *
 * <p>Used by {@link LegacyPlatformServerTypes} (generator/UI blocking) and by the assembly build test
 * {@code LegacyServerTypeForbiddenTest} so both paths share one implementation. The scanner:
 * <ul>
 *   <li>Reads {@code &lt;module&gt;} entries from the root {@code pom.xml} via Maven Model API</li>
 *   <li>For each module with an {@code AppsConnector} SPI file, collects config basenames
 *       ({@code src/main/resources/configs/*.json}) and runtime types ({@code getServerType()} via reflection)</li>
 *   <li>Builds the union of owned types, detects cross-module duplicates, and verifies config matches runtime</li>
 * </ul>
 *
 * <p>Requires connector classes to expose a public no-arg constructor so {@code getServerType()} can be invoked
 * during the scan (same constraint as the assembly guard).
 */
public final class SdkConnectorReactorScanner {

    /** Fully qualified SPI interface name for connector registration files. */
    private static final String APPS_CONNECTOR_SERVICE =
            "com.ovaledge.csp.v3.core.apps.service.AppsConnector";

    /** Relative path from a connector module root to its {@link #APPS_CONNECTOR_SERVICE} service file. */
    private static final String SPI_RELATIVE_PATH =
            "src/main/resources/META-INF/services/" + APPS_CONNECTOR_SERVICE;

    /**
     * Top-level directories that are not SDK connector modules but may exist beside connectors in the repo.
     * Used only when emitting orphan-directory warnings.
     */
    private static final Set<String> NON_CONNECTOR_MODULE_DIRS = Set.of(
            "assembly",
            "csp-api",
            "connector-archetype");

    private SdkConnectorReactorScanner() {}

    /**
     * Scans all connector modules listed in the root {@code pom.xml} under {@code repoRoot}.
     *
     * @param repoRoot directory that contains the aggregator {@code pom.xml} (typically the SDK repo root)
     * @return immutable summary of owned types, violations, and warnings; never {@code null}
     * @throws Exception if the root POM cannot be read or a connector class fails reflection (strict mode)
     */
    public static ScanResult scan(Path repoRoot) throws Exception {
        Path rootPom = repoRoot.resolve("pom.xml").normalize();
        List<String> reactorModules = readModules(rootPom);
        Set<String> reactorModuleSet = new LinkedHashSet<>(reactorModules);

        Set<String> ownedServerTypes = new TreeSet<>();
        Map<String, List<String>> modulesByConfigServerType = new LinkedHashMap<>();
        Map<String, List<String>> modulesByRuntimeServerType = new LinkedHashMap<>();
        Map<String, List<String>> modulesByProvider = new LinkedHashMap<>();
        List<String> configRuntimeMismatches = new ArrayList<>();
        List<String> runtimeInspectionErrors = new ArrayList<>();
        List<String> legacyViolationsFromConfig = new ArrayList<>();
        List<String> legacyViolationsFromRuntime = new ArrayList<>();

        for (String module : reactorModules) {
            Path modulePath = repoRoot.resolve(module);
            Path serviceFile = modulePath.resolve(SPI_RELATIVE_PATH);
            if (!Files.exists(serviceFile)) {
                continue;
            }

            Set<String> ownedBeforeModule = new TreeSet<>(ownedServerTypes);
            Set<String> moduleConfigTypes = new LinkedHashSet<>();
            Set<String> moduleRuntimeTypes = new LinkedHashSet<>();

            for (String provider : readServiceProviders(serviceFile)) {
                modulesByProvider.computeIfAbsent(provider, k -> new ArrayList<>()).add(module);
                try {
                    String runtimeServerType = readServerTypeFromProvider(provider);
                    if (runtimeServerType == null || runtimeServerType.trim().isEmpty()) {
                        runtimeInspectionErrors.add(
                                module + " -> " + provider + " returned empty getServerType()");
                    } else {
                        String normalizedRuntime = ServerTypeNormalizer.normalize(runtimeServerType);
                        moduleRuntimeTypes.add(normalizedRuntime);
                        registerServerTypeOwner(modulesByRuntimeServerType, normalizedRuntime, module);
                        ownedServerTypes.add(normalizedRuntime);
                        if (isLegacyViolationForModule(module, normalizedRuntime, ownedBeforeModule)) {
                            legacyViolationsFromRuntime.add(
                                    module + " -> " + normalizedRuntime + " (provider=" + provider + ")");
                        }
                    }
                } catch (Exception ex) {
                    runtimeInspectionErrors.add(
                            module + " -> " + provider + " failed getServerType(): " + ex.getMessage());
                }
            }

            for (String serverType : readServerTypesFromConfigs(
                    modulePath.resolve("src/main/resources/configs"))) {
                String normalized = ServerTypeNormalizer.normalize(serverType);
                moduleConfigTypes.add(normalized);
                registerServerTypeOwner(modulesByConfigServerType, normalized, module);
                ownedServerTypes.add(normalized);
                if (isLegacyViolationForModule(module, normalized, ownedBeforeModule)) {
                    legacyViolationsFromConfig.add(module + " -> " + normalized);
                }
            }

            checkConfigRuntimeMismatch(module, moduleConfigTypes, moduleRuntimeTypes, configRuntimeMismatches);
        }

        Set<String> ownedSnapshot = Collections.unmodifiableSet(new TreeSet<>(ownedServerTypes));
        List<String> orphanConnectorWarnings = detectOrphanConnectorDirs(repoRoot, reactorModuleSet);

        return new ScanResult(
                ownedSnapshot,
                modulesByConfigServerType,
                modulesByRuntimeServerType,
                modulesByProvider,
                configRuntimeMismatches,
                runtimeInspectionErrors,
                legacyViolationsFromConfig,
                legacyViolationsFromRuntime,
                orphanConnectorWarnings);
    }

    /**
     * Reports a legacy-platform txt conflict for this module when the type is forbidden, not yet owned by
     * another module, and the module directory name does not match the server type (cannot claim the name).
     */
    private static boolean isLegacyViolationForModule(
            String moduleName, String serverType, Set<String> ownedBeforeModule) {
        if (!LegacyPlatformServerTypes.isForbidden(serverType)) {
            return false;
        }
        String normalizedType = ServerTypeNormalizer.normalize(serverType);
        if (ownedBeforeModule.contains(normalizedType)) {
            return false;
        }
        return !ServerTypeNormalizer.normalize(moduleName).equals(normalizedType);
    }

    /** Records which reactor module(s) declare a given canonical server type. */
    private static void registerServerTypeOwner(
            Map<String, List<String>> modulesByServerType, String serverType, String module) {
        modulesByServerType.computeIfAbsent(serverType, k -> new ArrayList<>()).add(module);
    }

    /**
     * Fails when a module has both config and runtime types but they disagree (e.g. {@code configs/foo.json}
     * while {@code getServerType()} returns {@code bar}).
     */
    private static void checkConfigRuntimeMismatch(
            String module,
            Set<String> configTypes,
            Set<String> runtimeTypes,
            List<String> mismatches) {
        if (configTypes.isEmpty() || runtimeTypes.isEmpty()) {
            return;
        }
        if (runtimeTypes.size() > 1) {
            mismatches.add(module + " -> multiple runtime getServerType values: " + runtimeTypes);
            return;
        }
        String runtime = runtimeTypes.iterator().next();
        for (String configType : configTypes) {
            if (!configType.equals(runtime)) {
                mismatches.add(module + " -> config=" + configType + " runtime=" + runtime);
            }
        }
    }

    /**
     * Warns about connector-shaped directories on disk that are not listed in the root {@code pom.xml}
     * {@code &lt;modules&gt;} section (they will not ship in the reactor build).
     */
    private static List<String> detectOrphanConnectorDirs(Path repoRoot, Set<String> reactorModules)
            throws IOException {
        List<String> warnings = new ArrayList<>();
        if (!Files.isDirectory(repoRoot)) {
            return warnings;
        }
        try (Stream<Path> children = Files.list(repoRoot)) {
            children.filter(Files::isDirectory)
                    .forEach(dir -> {
                        String dirName = dir.getFileName().toString();
                        if (reactorModules.contains(dirName) || NON_CONNECTOR_MODULE_DIRS.contains(dirName)) {
                            return;
                        }
                        if (Files.exists(dir.resolve(SPI_RELATIVE_PATH))) {
                            warnings.add("Connector directory not listed in root pom.xml <modules>: " + dirName);
                        }
                    });
        }
        return warnings;
    }

    /** Reads {@code &lt;module&gt;} paths from the aggregator POM at {@code rootPom}. */
    private static List<String> readModules(Path rootPom) throws Exception {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try (FileReader fr = new FileReader(rootPom.toFile())) {
            Model model = reader.read(fr);
            return model.getModules() == null ? List.of() : model.getModules();
        }
    }

    /** Returns non-comment provider class names from a {@code META-INF/services} file. */
    private static List<String> readServiceProviders(Path serviceFile) throws IOException {
        List<String> providers = new ArrayList<>();
        for (String line : Files.readAllLines(serviceFile)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                providers.add(trimmed);
            }
        }
        return providers;
    }

    /**
     * Instantiates the connector with a no-arg constructor and invokes {@code getServerType()}.
     *
     * @param providerClassName FQCN listed in the SPI file
     * @return raw {@code getServerType()} result, or {@code null} if the method returned null
     */
    private static String readServerTypeFromProvider(String providerClassName) throws Exception {
        Class<?> connectorClass = Class.forName(providerClassName);
        Object connector = connectorClass.getDeclaredConstructor().newInstance();
        Method method = connectorClass.getMethod("getServerType");
        Object result = method.invoke(connector);
        return result == null ? null : result.toString();
    }

    /**
     * Derives server types from {@code configs/&lt;serverType&gt;.json} filenames (basename without {@code .json}).
     */
    private static List<String> readServerTypesFromConfigs(Path configsDir) throws IOException {
        if (!Files.exists(configsDir) || !Files.isDirectory(configsDir)) {
            return List.of();
        }
        List<String> types = new ArrayList<>();
        try (Stream<Path> paths = Files.list(configsDir)) {
            paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .forEach(types::add);
        }
        return types;
    }

    /** Deduplicates module names while preserving insertion order. */
    static List<String> uniqueModules(List<String> modules) {
        Set<String> set = new LinkedHashSet<>(modules);
        return new ArrayList<>(set);
    }

    /** Formats human-readable duplicate entries: {@code serverType -> [module-a, module-b]}. */
    static List<String> duplicateKeys(Map<String, List<String>> ownersByKey) {
        List<String> duplicates = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : ownersByKey.entrySet()) {
            List<String> modules = uniqueModules(entry.getValue());
            if (modules.size() > 1) {
                duplicates.add(entry.getKey() + " -> " + modules);
            }
        }
        return duplicates;
    }

    /**
     * Immutable outcome of {@link #scan(Path)} for assembly tests and {@link LegacyPlatformServerTypes}.
     */
    public static final class ScanResult {

        /** Canonical server types claimed by any reactor connector (config basenames ∪ runtime). */
        private final Set<String> ownedServerTypes;

        /** Maps canonical server type to module(s) that declare it via config JSON basename. */
        private final Map<String, List<String>> modulesByConfigServerType;

        /** Maps canonical server type to module(s) that declare it via {@code getServerType()}. */
        private final Map<String, List<String>> modulesByRuntimeServerType;

        /** Maps {@code AppsConnector} implementation FQCN to module(s) that register it in SPI. */
        private final Map<String, List<String>> modulesByProvider;

        private final List<String> configRuntimeMismatches;
        private final List<String> runtimeInspectionErrors;
        private final List<String> legacyViolationsFromConfig;
        private final List<String> legacyViolationsFromRuntime;
        private final List<String> orphanConnectorWarnings;

        ScanResult(
                Set<String> ownedServerTypes,
                Map<String, List<String>> modulesByConfigServerType,
                Map<String, List<String>> modulesByRuntimeServerType,
                Map<String, List<String>> modulesByProvider,
                List<String> configRuntimeMismatches,
                List<String> runtimeInspectionErrors,
                List<String> legacyViolationsFromConfig,
                List<String> legacyViolationsFromRuntime,
                List<String> orphanConnectorWarnings) {
            this.ownedServerTypes = ownedServerTypes;
            this.modulesByConfigServerType = modulesByConfigServerType;
            this.modulesByRuntimeServerType = modulesByRuntimeServerType;
            this.modulesByProvider = modulesByProvider;
            this.configRuntimeMismatches = List.copyOf(configRuntimeMismatches);
            this.runtimeInspectionErrors = List.copyOf(runtimeInspectionErrors);
            this.legacyViolationsFromConfig = List.copyOf(legacyViolationsFromConfig);
            this.legacyViolationsFromRuntime = List.copyOf(legacyViolationsFromRuntime);
            this.orphanConnectorWarnings = List.copyOf(orphanConnectorWarnings);
        }

        /**
         * All canonical server types currently used by SDK modules in the reactor (union of config and runtime).
         * Combined with the legacy txt list to block new connector names in the generator.
         */
        public Set<String> ownedServerTypes() {
            return ownedServerTypes;
        }

        /** Module ownership keyed by config-derived server type (for duplicate detection). */
        public Map<String, List<String>> modulesByConfigServerType() {
            return modulesByConfigServerType;
        }

        /** Module ownership keyed by runtime {@code getServerType()} (for duplicate detection). */
        public Map<String, List<String>> modulesByRuntimeServerType() {
            return modulesByRuntimeServerType;
        }

        /** Module ownership keyed by SPI provider FQCN (duplicate FQCN across modules fails the build). */
        public Map<String, List<String>> modulesByProvider() {
            return modulesByProvider;
        }

        /**
         * Per-module lines where {@code configs/*.json} basename does not match {@code getServerType()}
         * (build must fail).
         */
        public List<String> configRuntimeMismatches() {
            return configRuntimeMismatches;
        }

        /**
         * Per-module lines where the connector class could not be loaded or {@code getServerType()} failed
         * (build must fail).
         */
        public List<String> runtimeInspectionErrors() {
            return runtimeInspectionErrors;
        }

        /** Legacy txt conflicts detected from config JSON basenames. */
        public List<String> legacyViolationsFromConfig() {
            return legacyViolationsFromConfig;
        }

        /** Legacy txt conflicts detected from runtime {@code getServerType()}. */
        public List<String> legacyViolationsFromRuntime() {
            return legacyViolationsFromRuntime;
        }

        /**
         * Non-fatal warnings: directories with an SPI file but missing from root {@code pom.xml &lt;modules&gt;}.
         */
        public List<String> orphanConnectorWarnings() {
            return orphanConnectorWarnings;
        }

        /** Duplicate canonical server types inferred from config filenames across distinct modules. */
        public List<String> duplicateServerTypesFromConfig() {
            return duplicateKeys(modulesByConfigServerType);
        }

        /** Duplicate canonical server types inferred from {@code getServerType()} across distinct modules. */
        public List<String> duplicateServerTypesFromRuntime() {
            return duplicateKeys(modulesByRuntimeServerType);
        }

        /** Duplicate {@code AppsConnector} provider implementation classes across distinct modules. */
        public List<String> duplicateProviderViolations() {
            return duplicateKeys(modulesByProvider);
        }
    }
}
