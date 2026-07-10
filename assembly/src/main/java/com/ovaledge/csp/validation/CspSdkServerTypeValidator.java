package com.ovaledge.csp.validation;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Build-time guard for SDK connector {@code serverType} policy.
 *
 * <p>Replaces {@code LegacyServerTypeForbiddenTest}. Delegates to {@link SdkConnectorReactorScanner}
 * so CI uses the same rules as the Connector Generator and {@link LegacyPlatformServerTypes}.
 * Fails the build on legacy txt conflicts (unowned), duplicate types, duplicate SPI providers,
 * config/runtime mismatches, or reflection errors.
 */
@Mojo(name = "validate-csp-sdk-server-types", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = false)
public class CspSdkServerTypeValidator extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File basedir;

    @Parameter(property = "skip.csp.sdk.server.type.validation", defaultValue = "false")
    private boolean skip;

    public static void main(String[] args) {
        try {
            CspSdkServerTypeValidator validator = new CspSdkServerTypeValidator();
            validator.basedir = new File(System.getProperty("project.basedir", "."));
            validator.setLog(new SimpleSystemLog());
            validator.execute();
        } catch (Exception e) {
            System.err.println("Error executing CspSdkServerTypeValidator: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping csp-sdk server type validation");
            return;
        }

        Path repoRoot = Paths.get(basedir.getAbsolutePath(), "..").normalize();
        getLog().info("Validating SDK connector server types under " + repoRoot);

        try {
            SdkConnectorReactorScanner.ScanResult scan = SdkConnectorReactorScanner.scan(repoRoot);

            for (String warning : scan.orphanConnectorWarnings()) {
                getLog().warn(warning);
            }

            List<String> duplicateViolationsFromConfig = scan.duplicateServerTypesFromConfig();
            List<String> duplicateViolationsFromRuntime = scan.duplicateServerTypesFromRuntime();
            List<String> duplicateProviderViolations = scan.duplicateProviderViolations();

            boolean passed = scan.legacyViolationsFromConfig().isEmpty()
                    && duplicateViolationsFromConfig.isEmpty()
                    && scan.legacyViolationsFromRuntime().isEmpty()
                    && duplicateViolationsFromRuntime.isEmpty()
                    && scan.configRuntimeMismatches().isEmpty()
                    && scan.runtimeInspectionErrors().isEmpty()
                    && duplicateProviderViolations.isEmpty();

            if (!passed) {
                String message = "SDK connector(s) failed serverType validation. "
                        + "Legacy conflicts(config): " + scan.legacyViolationsFromConfig()
                        + " | Duplicate serverTypes(config): " + duplicateViolationsFromConfig
                        + " | Legacy conflicts(runtime getServerType): " + scan.legacyViolationsFromRuntime()
                        + " | Duplicate serverTypes(runtime getServerType): " + duplicateViolationsFromRuntime
                        + " | Config/runtime mismatches: " + scan.configRuntimeMismatches()
                        + " | Runtime inspection errors: " + scan.runtimeInspectionErrors()
                        + " | Duplicate providers: " + duplicateProviderViolations;
                getLog().error(message);
                throw new MojoExecutionException(message);
            }

            getLog().info("SDK connector server type validation passed (" + scan.ownedServerTypes().size() + " types)");
        } catch (MojoExecutionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MojoExecutionException("Failed to validate SDK connector server types under " + repoRoot, ex);
        }
    }

    private static class SimpleSystemLog implements org.apache.maven.plugin.logging.Log {
        @Override
        public boolean isDebugEnabled() { return false; }
        @Override
        public void debug(CharSequence content) { }
        @Override
        public void debug(CharSequence content, Throwable error) { }
        @Override
        public void debug(Throwable error) { }
        @Override
        public boolean isInfoEnabled() { return true; }
        @Override
        public void info(CharSequence content) { System.out.println(content); }
        @Override
        public void info(CharSequence content, Throwable error) { System.out.println(content); }
        @Override
        public void info(Throwable error) { error.printStackTrace(); }
        @Override
        public boolean isWarnEnabled() { return true; }
        @Override
        public void warn(CharSequence content) { System.err.println("[WARN] " + content); }
        @Override
        public void warn(CharSequence content, Throwable error) { System.err.println("[WARN] " + content); }
        @Override
        public void warn(Throwable error) { error.printStackTrace(); }
        @Override
        public boolean isErrorEnabled() { return true; }
        @Override
        public void error(CharSequence content) { System.err.println("[ERROR] " + content); }
        @Override
        public void error(CharSequence content, Throwable error) { System.err.println("[ERROR] " + content); }
        @Override
        public void error(Throwable error) { error.printStackTrace(); }
    }
}
