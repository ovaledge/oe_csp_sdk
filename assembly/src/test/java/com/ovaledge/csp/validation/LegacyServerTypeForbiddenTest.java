package com.ovaledge.csp.validation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Assembly-phase build guard for SDK connector {@code serverType} policy.
 *
 * <p>Delegates to {@link SdkConnectorReactorScanner} from the test-scoped {@code csp-api} dependency
 * (see {@code assembly/pom.xml}) so CI uses the same rules as the Connector Generator and
 * {@link LegacyPlatformServerTypes}. Fails the build on legacy txt conflicts (unowned), duplicate types,
 * duplicate SPI providers, config/runtime mismatches, or reflection errors. Orphan connector directories
 * (SPI present but not in root {@code pom.xml &lt;modules&gt;}) are logged as warnings only.
 */
public class LegacyServerTypeForbiddenTest {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyServerTypeForbiddenTest.class);

    /**
     * Fails when any connector violates legacy reservation, duplicates a server type, or reuses a provider class.
     */
    @Test
    public void sdkConnectors_mustNotUseForbiddenLegacyPlatformServerTypes() throws Exception {
        Path repoRoot = Paths.get("..").normalize();
        SdkConnectorReactorScanner.ScanResult scan = SdkConnectorReactorScanner.scan(repoRoot);

        for (String warning : scan.orphanConnectorWarnings()) {
            LOG.warn("{}", warning);
        }

        List<String> duplicateViolationsFromConfig = scan.duplicateServerTypesFromConfig();
        List<String> duplicateViolationsFromRuntime = scan.duplicateServerTypesFromRuntime();
        List<String> duplicateProviderViolations = scan.duplicateProviderViolations();

        assertTrue(
                scan.legacyViolationsFromConfig().isEmpty()
                        && duplicateViolationsFromConfig.isEmpty()
                        && scan.legacyViolationsFromRuntime().isEmpty()
                        && duplicateViolationsFromRuntime.isEmpty()
                        && scan.configRuntimeMismatches().isEmpty()
                        && scan.runtimeInspectionErrors().isEmpty()
                        && duplicateProviderViolations.isEmpty(),
                "SDK connector(s) failed serverType validation. "
                        + "Legacy conflicts(config): " + scan.legacyViolationsFromConfig()
                        + " | Duplicate serverTypes(config): " + duplicateViolationsFromConfig
                        + " | Legacy conflicts(runtime getServerType): " + scan.legacyViolationsFromRuntime()
                        + " | Duplicate serverTypes(runtime getServerType): " + duplicateViolationsFromRuntime
                        + " | Config/runtime mismatches: " + scan.configRuntimeMismatches()
                        + " | Runtime inspection errors: " + scan.runtimeInspectionErrors()
                        + " | Duplicate providers: " + duplicateProviderViolations);
    }
}
