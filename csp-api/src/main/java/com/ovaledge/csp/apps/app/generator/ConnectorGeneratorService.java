package com.ovaledge.csp.apps.app.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovaledge.csp.validation.LegacyPlatformServerTypes;
import com.ovaledge.csp.validation.ServerTypeNormalizer;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ConnectorGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorGeneratorService.class);

    private static final int ICON_MAX_BYTES = 200 * 1024;
    private static final int ICON_SIZE_64 = 64;
    private static final int ICON_SIZE_128 = 128;
    private static final String MODULE_MARKER_NEW =
            "<!-- Connector generator marker (modules): DO NOT REMOVE. New connector modules are inserted above this line. -->";
    private static final String MODULE_MARKER_APPS =
            "<!-- Add new Connector modules here -->";
    private static final String DEP_MARKER_NEW =
            "<!-- Connector generator marker (dependencies): DO NOT REMOVE. New connector dependencies are inserted above this line. -->";
    private static final String TEST_REPORT_MODULES_OPEN = "<csp.sdk.test.report.modules>";
    private static final String TEST_REPORT_MODULES_CLOSE = "</csp.sdk.test.report.modules>";

    private final TemplateEngine templateEngine = new TemplateEngine();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private record IconSpec(String extension, byte[] bytes) {}

    private static String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
    }

    public ConnectorGeneratorResult generate(ConnectorGeneratorRequest request, MultipartFile icon) {
        Path repoRootPath = resolveRepoRootPath(request != null ? request.getRepoRoot() : null);
        ConnectorGenerationContext context = validateAndNormalize(request, repoRootPath);
        IconSpec iconSpec = validateIcon(icon);
        String iconExtension = iconSpec.extension();

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("connector-generator-");
            Path moduleRoot = tempDir.resolve(context.getArtifactId());
            Files.createDirectories(moduleRoot);

            Map<String, String> templateValues = TemplateValues.from(context);
            applyRepoParentVersion(templateValues, request != null ? request.getRepoRoot() : null);
            templateValues.put("iconExtension", iconExtension);
            boolean profilingEnabled = isProfilingEnabled(request);
            String profilingMode = resolveProfilingMode(request, profilingEnabled);
            TemplateValues.applyProfiling(
                    templateValues,
                    context.getClassPrefix(),
                    profilingEnabled,
                    profilingMode,
                    context.getObjectKinds());

            // Use connector-archetype templates as the single source of truth
            writeTemplate(moduleRoot.resolve("pom.xml"), "archetype-resources/pom.xml", templateValues);
            writeTemplate(moduleRoot.resolve("INSTRUCTIONS.txt"), "archetype-resources/INSTRUCTIONS.txt", templateValues);

            Path javaBase = moduleRoot.resolve("src/main/java/com/ovaledge/csp/apps/" + context.getPackageName());
            writeTemplate(javaBase.resolve("main/" + context.getClassPrefix() + "Connector.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/main/__classPrefix__Connector.java",
                    templateValues);
            writeTemplate(javaBase.resolve("main/" + context.getClassPrefix() + "MetadataService.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/main/__classPrefix__MetadataService.java",
                    templateValues);
            writeTemplate(javaBase.resolve("main/" + context.getClassPrefix() + "QueryService.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/main/__classPrefix__QueryService.java",
                    templateValues);
            if (profilingEnabled) {
                writeTemplate(javaBase.resolve("main/" + context.getClassPrefix() + "ProfilingService.java"),
                        "archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/main/__classPrefix__ProfilingService.java",
                        templateValues);
            }
            writeTemplate(javaBase.resolve("constants/" + context.getClassPrefix() + "Constants.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/constants/__classPrefix__Constants.java",
                    templateValues);
            writeTemplate(javaBase.resolve("quick/" + context.getClassPrefix() + "Controller.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/quick/__classPrefix__Controller.java",
                    templateValues);
            writeTemplate(javaBase.resolve("quick/" + context.getClassPrefix() + "QuickApplication.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/quick/__classPrefix__QuickApplication.java",
                    templateValues);

            writeGeneratedUnitTests(moduleRoot, context, templateValues, profilingEnabled);

            Path resourcesBase = moduleRoot.resolve("src/main/resources");
            Path servicesPath = resourcesBase.resolve("META-INF/services");
            Files.createDirectories(servicesPath);
            writeTemplate(
                    servicesPath.resolve("com.ovaledge.csp.v3.core.apps.service.AppsConnector"),
                    "archetype-resources/src/main/resources/META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector",
                    templateValues);

            writeCapabilityManifest(
                    resourcesBase.resolve("configs/" + context.getServerType() + ".json"),
                    context,
                    request,
                    iconExtension);
            writeReferencesMarkdown(resourcesBase.resolve("references.md"),
                    request != null ? request.getReferences() : List.of());

            Path iconsPath = resourcesBase.resolve("icons");
            Files.createDirectories(iconsPath);
            Files.write(iconsPath.resolve(context.getArtifactId() + "." + iconExtension), iconSpec.bytes());

            byte[] zipBytes = zipDirectory(moduleRoot);
            return new ConnectorGeneratorResult(context.getArtifactId() + "-connector.zip", zipBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate connector module", e);
        } finally {
            if (tempDir != null) {
                deleteDirectoryQuietly(tempDir);
            }
        }
    }

    /**
     * Generates a new connector project on the server filesystem under {@code repoRoot}.
     * <p>
     * NOTE: The existing zip-based {@link #generate(ConnectorGeneratorRequest, MultipartFile)} method is intentionally
     * preserved for potential future reuse.
     */
    public Map<String, String> generateToDirectory(ConnectorGeneratorRequest request, MultipartFile icon, String repoRoot) {
        List<String> errors = new ArrayList<>();
        if (repoRoot == null || repoRoot.trim().isEmpty()) {
            errors.add("Repository root path (repoRoot) is required.");
        }

        Path repoRootPath = null;
        if (errors.isEmpty()) {
            try {
                Path raw = Paths.get(repoRoot.trim());
                if (raw.isAbsolute()) {
                    repoRootPath = raw.normalize();
                } else {
                    Path base = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
                    repoRootPath = base.resolve(raw).normalize();
                }
            } catch (InvalidPathException ex) {
                errors.add("Invalid repository root path (repoRoot).");
            }
        }

        if (errors.isEmpty()) {
            if (!Files.exists(repoRootPath)) {
                errors.add("Repository root path does not exist: " + repoRootPath
                        + " (hint: use an absolute path like /Users/.../oe_csp_sdk)");
            } else if (!Files.isDirectory(repoRootPath)) {
                errors.add("Repository root path is not a directory: " + repoRootPath);
            }
        }
        Path parentPomPath = null;
        Path cspApiPomPath = null;
        Path assemblyPomPath = null;
        if (errors.isEmpty()) {
            parentPomPath = repoRootPath.resolve("pom.xml").normalize();
            cspApiPomPath = repoRootPath.resolve("csp-api/pom.xml").normalize();
            assemblyPomPath = repoRootPath.resolve("assembly/pom.xml").normalize();
            if (!Files.exists(parentPomPath)) {
                errors.add("Missing required file: " + parentPomPath);
            }
            if (!Files.exists(cspApiPomPath)) {
                errors.add("Missing required file: " + cspApiPomPath);
            }
            if (!Files.exists(assemblyPomPath)) {
                errors.add("Missing required file: " + assemblyPomPath);
            }
        }

        if (!errors.isEmpty()) {
            throw new ConnectorGeneratorValidationException(errors);
        }

        ConnectorGenerationContext context = validateAndNormalize(request, repoRootPath);
        IconSpec iconSpec = validateIcon(icon);
        String iconExtension = iconSpec.extension();

        Path moduleRoot = repoRootPath.resolve(context.getArtifactId()).normalize();
        boolean overwriteExistingModule = request != null && Boolean.TRUE.equals(request.getOverwriteExistingModule());
        if (Files.exists(moduleRoot)) {
            if (!overwriteExistingModule) {
                throw new ConnectorGeneratorValidationException(
                        List.of("Target folder already exists: " + moduleRoot
                                + ". Confirm overwrite to delete and regenerate."));
            }
            try {
                deleteModuleDirectoryForOverwrite(repoRootPath, moduleRoot);
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete existing module for overwrite: " + moduleRoot, e);
            }
        }

        try {
            Files.createDirectories(moduleRoot);

            Map<String, String> templateValues = TemplateValues.from(context);
            applyRepoParentVersion(templateValues, repoRootPath);
            templateValues.put("iconExtension", iconExtension);
            boolean profilingEnabled = isProfilingEnabled(request);
            String profilingMode = resolveProfilingMode(request, profilingEnabled);
            TemplateValues.applyProfiling(
                    templateValues,
                    context.getClassPrefix(),
                    profilingEnabled,
                    profilingMode,
                    context.getObjectKinds());

            // Use connector-archetype templates as the single source of truth
            writeTemplate(moduleRoot.resolve("pom.xml"), "archetype-resources/pom.xml", templateValues);

            Path javaBase = moduleRoot.resolve("src/main/java/com/ovaledge/csp/apps/" + context.getPackageName());
            writeTemplate(javaBase.resolve("main/" + context.getClassPrefix() + "Connector.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/main/__classPrefix__Connector.java",
                    templateValues);
            writeTemplate(javaBase.resolve("main/" + context.getClassPrefix() + "MetadataService.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/main/__classPrefix__MetadataService.java",
                    templateValues);
            writeTemplate(javaBase.resolve("main/" + context.getClassPrefix() + "QueryService.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/main/__classPrefix__QueryService.java",
                    templateValues);
            if (profilingEnabled) {
                writeTemplate(javaBase.resolve("main/" + context.getClassPrefix() + "ProfilingService.java"),
                        "archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/main/__classPrefix__ProfilingService.java",
                        templateValues);
            }
            writeTemplate(javaBase.resolve("constants/" + context.getClassPrefix() + "Constants.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/constants/__classPrefix__Constants.java",
                    templateValues);
            writeTemplate(javaBase.resolve("quick/" + context.getClassPrefix() + "Controller.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/quick/__classPrefix__Controller.java",
                    templateValues);
            writeTemplate(javaBase.resolve("quick/" + context.getClassPrefix() + "QuickApplication.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__packageName__/quick/__classPrefix__QuickApplication.java",
                    templateValues);

            writeGeneratedUnitTests(moduleRoot, context, templateValues, profilingEnabled);

            Path resourcesBase = moduleRoot.resolve("src/main/resources");
            Path servicesPath = resourcesBase.resolve("META-INF/services");
            Files.createDirectories(servicesPath);
            writeTemplate(
                    servicesPath.resolve("com.ovaledge.csp.v3.core.apps.service.AppsConnector"),
                    "archetype-resources/src/main/resources/META-INF/services/com.ovaledge.csp.v3.core.apps.service.AppsConnector",
                    templateValues);

            writeCapabilityManifest(
                    resourcesBase.resolve("configs/" + context.getServerType() + ".json"),
                    context,
                    request,
                    iconExtension);
            writeReferencesMarkdown(resourcesBase.resolve("references.md"),
                    request != null ? request.getReferences() : List.of());

            Path iconsPath = resourcesBase.resolve("icons");
            Files.createDirectories(iconsPath);
            Files.write(iconsPath.resolve(context.getArtifactId() + "." + iconExtension), iconSpec.bytes());

            boolean parentModuleAdded;
            boolean parentDependencyManagementAdded;
            boolean cspApiDependencyAdded;
            boolean assemblyDependencyAdded;
            boolean testReportModulesAdded;
            try {
                PomWiringResult wiring = applyPomWiring(
                        parentPomPath, cspApiPomPath, assemblyPomPath, context.getArtifactId());
                parentModuleAdded = wiring.parentModuleAdded();
                parentDependencyManagementAdded = wiring.parentDependencyManagementAdded();
                cspApiDependencyAdded = wiring.cspApiDependencyAdded();
                assemblyDependencyAdded = wiring.assemblyDependencyAdded();
                testReportModulesAdded = wiring.testReportModulesAdded();
            } catch (IOException e) {
                deleteDirectoryQuietly(moduleRoot);
                throw new RuntimeException("Failed to wire connector module into pom files", e);
            }

            Map<String, String> response = new LinkedHashMap<>();
            response.put("artifactId", context.getArtifactId());
            response.put("generatedPath", moduleRoot.toString());
            response.put("wiringApplied", "true");
            response.put("parentModuleAdded", String.valueOf(parentModuleAdded));
            response.put("parentDependencyManagementAdded", String.valueOf(parentDependencyManagementAdded));
            response.put("cspApiDependencyAdded", String.valueOf(cspApiDependencyAdded));
            response.put("assemblyDependencyAdded", String.valueOf(assemblyDependencyAdded));
            response.put("testReportModulesAdded", String.valueOf(testReportModulesAdded));
            if (!parentDependencyManagementAdded) {
                response.put("parentDependencyManagementReason",
                        "Dependency already exists in parent pom.xml dependencyManagement.");
            }
            if (!cspApiDependencyAdded) {
                response.put("cspApiDependencyReason", "Dependency already exists in csp-api/pom.xml.");
            }
            if (!assemblyDependencyAdded) {
                response.put("assemblyDependencyReason", "Dependency already exists in assembly/pom.xml.");
            }
            if (!testReportModulesAdded) {
                response.put("testReportModulesReason",
                        "Module already listed in csp.sdk.test.report.modules or property is absent.");
            }
            return response;
        } catch (IOException e) {
            deleteDirectoryQuietly(moduleRoot);
            throw new RuntimeException("Failed to generate connector module", e);
        }
    }

    private record PomUpsertResult(String content, boolean changed) {}

    private record PomWiringResult(
            boolean parentModuleAdded,
            boolean parentDependencyManagementAdded,
            boolean cspApiDependencyAdded,
            boolean assemblyDependencyAdded,
            boolean testReportModulesAdded) {}

    /**
     * Computes all pom mutations in memory, then flushes them in one pass so partial pom writes cannot
     * leave the reactor referencing a module that was rolled back.
     */
    private PomWiringResult applyPomWiring(
            Path parentPomPath, Path cspApiPomPath, Path assemblyPomPath, String artifactId) throws IOException {
        String originalParentPom = Files.readString(parentPomPath, StandardCharsets.UTF_8);
        String originalCspApiPom = Files.readString(cspApiPomPath, StandardCharsets.UTF_8);
        String originalAssemblyPom = Files.readString(assemblyPomPath, StandardCharsets.UTF_8);

        String parentPom = originalParentPom;
        PomUpsertResult parentModule = upsertParentModuleInMemory(parentPom, artifactId);
        parentPom = parentModule.content();
        PomUpsertResult parentDependencyManagement = upsertParentDependencyManagementInMemory(parentPom, artifactId);
        parentPom = parentDependencyManagement.content();
        PomUpsertResult testReportModules = upsertTestReportModulesInMemory(parentPom, artifactId);
        parentPom = testReportModules.content();

        PomUpsertResult cspApiDependency = upsertModuleDependencyInMemory(originalCspApiPom, artifactId);
        PomUpsertResult assemblyDependency = upsertModuleDependencyInMemory(originalAssemblyPom, artifactId);

        Map<Path, String> pendingWrites = new LinkedHashMap<>();
        if (!parentPom.equals(originalParentPom)) {
            pendingWrites.put(parentPomPath, parentPom);
        }
        if (!cspApiDependency.content().equals(originalCspApiPom)) {
            pendingWrites.put(cspApiPomPath, cspApiDependency.content());
        }
        if (!assemblyDependency.content().equals(originalAssemblyPom)) {
            pendingWrites.put(assemblyPomPath, assemblyDependency.content());
        }
        for (Map.Entry<Path, String> entry : pendingWrites.entrySet()) {
            Files.writeString(entry.getKey(), entry.getValue(), StandardCharsets.UTF_8);
        }

        return new PomWiringResult(
                parentModule.changed(),
                parentDependencyManagement.changed(),
                cspApiDependency.changed(),
                assemblyDependency.changed(),
                testReportModules.changed());
    }

    private PomUpsertResult upsertParentModuleInMemory(String pom, String artifactId) throws IOException {
        String marker = firstMarkerPresent(pom, MODULE_MARKER_NEW, MODULE_MARKER_APPS);
        String moduleLine = "    <module>" + artifactId + "</module>";
        if (pom.contains("<module>" + artifactId + "</module>")) {
            return new PomUpsertResult(pom, false);
        }
        String updated;
        if (marker != null) {
            updated = pom.replace(marker, moduleLine + "\n" + marker);
        } else {
            updated = insertBeforeClosingTagInSection(
                    pom,
                    "<modules>",
                    "</modules>",
                    moduleLine + "\n",
                    "parent pom.xml modules section");
        }
        return new PomUpsertResult(updated, true);
    }

    private void deleteModuleDirectoryForOverwrite(Path repoRootPath, Path moduleRoot) throws IOException {
        Path normalizedRepoRoot = repoRootPath.toAbsolutePath().normalize();
        Path normalizedModuleRoot = moduleRoot.toAbsolutePath().normalize();
        if (!normalizedModuleRoot.startsWith(normalizedRepoRoot)) {
            throw new IOException("Refusing to delete path outside repository root: " + normalizedModuleRoot);
        }
        if (normalizedModuleRoot.equals(normalizedRepoRoot)) {
            throw new IOException("Refusing to delete repository root: " + normalizedModuleRoot);
        }
        if (!Files.exists(normalizedModuleRoot)) {
            return;
        }
        Files.walkFileTree(normalizedModuleRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private PomUpsertResult upsertParentDependencyManagementInMemory(String pom, String artifactId) throws IOException {
        String marker = firstMarkerPresent(pom, DEP_MARKER_NEW);
        String depSnippet = "      <dependency>\n"
                + "        <groupId>com.ovaledge</groupId>\n"
                + "        <artifactId>" + artifactId + "</artifactId>\n"
                + "        <version>${project.version}</version>\n"
                + "      </dependency>";
        if (pom.contains("<artifactId>" + artifactId + "</artifactId>")) {
            return new PomUpsertResult(pom, false);
        }
        String updated;
        if (marker != null) {
            updated = pom.replace(marker, depSnippet + "\n" + marker);
        } else {
            updated = insertBeforeClosingTagInNestedSection(
                    pom,
                    "<dependencyManagement>",
                    "<dependencies>",
                    "</dependencies>",
                    depSnippet + "\n",
                    "parent pom.xml dependencyManagement dependencies");
        }
        return new PomUpsertResult(updated, true);
    }

    private PomUpsertResult upsertModuleDependencyInMemory(String pom, String artifactId) throws IOException {
        String marker = firstMarkerPresent(pom, DEP_MARKER_NEW);
        String depSnippet = "        <dependency>\n"
                + "            <groupId>com.ovaledge</groupId>\n"
                + "            <artifactId>" + artifactId + "</artifactId>\n"
                + "        </dependency>";
        if (pom.contains("<artifactId>" + artifactId + "</artifactId>")) {
            return new PomUpsertResult(pom, false);
        }
        String updated;
        if (marker != null) {
            updated = pom.replace(marker, depSnippet + "\n" + marker);
        } else {
            updated = insertBeforeClosingTagInSection(
                    pom,
                    "<dependencies>",
                    "</dependencies>",
                    depSnippet + "\n",
                    "module pom.xml dependencies section",
                    "<!-- Test dependencies");
        }
        return new PomUpsertResult(updated, true);
    }

    /**
     * No-op when {@code csp.sdk.test.report.modules} is absent.
     * No-op when the property is absent (e.g. SDK reactor).
     */
    private PomUpsertResult upsertTestReportModulesInMemory(String pom, String artifactId) throws IOException {
        int propertyStart = pom.indexOf(TEST_REPORT_MODULES_OPEN);
        if (propertyStart < 0) {
            return new PomUpsertResult(pom, false);
        }
        int valueStart = propertyStart + TEST_REPORT_MODULES_OPEN.length();
        int propertyEnd = pom.indexOf(TEST_REPORT_MODULES_CLOSE, valueStart);
        if (propertyEnd < 0) {
            throw new IOException("Malformed parent pom.xml: unclosed csp.sdk.test.report.modules property.");
        }
        String rawValue = pom.substring(valueStart, propertyEnd);
        String trimmedValue = rawValue.trim();
        if (containsCommaSeparatedToken(trimmedValue, artifactId)) {
            return new PomUpsertResult(pom, false);
        }
        String updatedInner = trimmedValue.isEmpty() ? artifactId : trimmedValue + "," + artifactId;
        int leadingWhitespaceEnd = 0;
        while (leadingWhitespaceEnd < rawValue.length()
                && Character.isWhitespace(rawValue.charAt(leadingWhitespaceEnd))) {
            leadingWhitespaceEnd++;
        }
        int trailingWhitespaceStart = rawValue.length();
        while (trailingWhitespaceStart > leadingWhitespaceEnd
                && Character.isWhitespace(rawValue.charAt(trailingWhitespaceStart - 1))) {
            trailingWhitespaceStart--;
        }
        String prefix = rawValue.substring(0, leadingWhitespaceEnd);
        String suffix = rawValue.substring(trailingWhitespaceStart);
        String updatedValue = prefix + updatedInner + suffix;
        String updated = pom.substring(0, valueStart) + updatedValue + pom.substring(propertyEnd);
        return new PomUpsertResult(updated, true);
    }

    private boolean containsCommaSeparatedToken(String csv, String token) {
        if (csv == null || csv.isBlank()) {
            return false;
        }
        for (String part : csv.split(",")) {
            if (token.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private String firstMarkerPresent(String content, String... markers) {
        for (String marker : markers) {
            if (content.contains(marker)) {
                return marker;
            }
        }
        return null;
    }

    private String insertBeforeClosingTagInSection(
            String xml,
            String sectionOpenTag,
            String sectionCloseTag,
            String snippetWithTrailingNewline,
            String sectionLabel) throws IOException {
        return insertBeforeClosingTagInSection(
                xml, sectionOpenTag, sectionCloseTag, snippetWithTrailingNewline, sectionLabel, null);
    }

    /**
     * Inserts before {@code sectionCloseTag} inside {@code sectionOpenTag}. When {@code stopBeforeMarker}
     * is set, closes at the first occurrence of that text after the open tag (e.g. assembly test deps).
     */
    private String insertBeforeClosingTagInSection(
            String xml,
            String sectionOpenTag,
            String sectionCloseTag,
            String snippetWithTrailingNewline,
            String sectionLabel,
            String stopBeforeMarker) throws IOException {
        int sectionStart = xml.indexOf(sectionOpenTag);
        if (sectionStart < 0) {
            throw new IOException("Unable to locate " + sectionLabel + " (missing " + sectionOpenTag + ").");
        }
        int sectionEnd;
        if (stopBeforeMarker != null) {
            int markerIndex = xml.indexOf(stopBeforeMarker, sectionStart);
            if (markerIndex >= 0) {
                sectionEnd = findLastCloseTagBefore(xml, sectionCloseTag, sectionStart, markerIndex);
                if (sectionEnd < 0) {
                    sectionEnd = markerIndex;
                }
            } else {
                sectionEnd = xml.indexOf(sectionCloseTag, sectionStart);
            }
        } else {
            sectionEnd = xml.indexOf(sectionCloseTag, sectionStart);
        }
        if (sectionEnd < 0) {
            throw new IOException("Unable to locate " + sectionLabel + " (missing " + sectionCloseTag + ").");
        }
        return xml.substring(0, sectionEnd) + snippetWithTrailingNewline + xml.substring(sectionEnd);
    }

    private int findLastCloseTagBefore(String xml, String closeTag, int sectionStart, int beforeIndex) {
        int last = -1;
        int searchFrom = sectionStart;
        while (true) {
            int idx = xml.indexOf(closeTag, searchFrom);
            if (idx < 0 || idx >= beforeIndex) {
                break;
            }
            last = idx;
            searchFrom = idx + closeTag.length();
        }
        return last;
    }

    private String insertBeforeClosingTagInNestedSection(
            String xml,
            String outerOpenTag,
            String innerOpenTag,
            String innerCloseTag,
            String snippetWithTrailingNewline,
            String sectionLabel) throws IOException {
        int outerStart = xml.indexOf(outerOpenTag);
        if (outerStart < 0) {
            throw new IOException("Unable to locate " + sectionLabel + " (missing " + outerOpenTag + ").");
        }
        int innerStart = xml.indexOf(innerOpenTag, outerStart);
        if (innerStart < 0) {
            throw new IOException("Unable to locate " + sectionLabel + " (missing " + innerOpenTag + ").");
        }
        int innerEnd = xml.indexOf(innerCloseTag, innerStart);
        if (innerEnd < 0) {
            throw new IOException("Unable to locate " + sectionLabel + " (missing " + innerCloseTag + ").");
        }
        return xml.substring(0, innerEnd) + snippetWithTrailingNewline + xml.substring(innerEnd);
    }

    /**
     * Validates generator input and derives artifact id, package name, and class prefix.
     *
     * <p>Rejects connector names on the legacy txt list or already used by an in-repo Apps module.
     */
    private ConnectorGenerationContext validateAndNormalize(ConnectorGeneratorRequest request, Path repoRootPath) {
        List<String> errors = new ArrayList<>();
        String connectorName = request != null ? request.getConnectorName() : null;

        if (repoRootPath == null || !Files.exists(repoRootPath) || !Files.isDirectory(repoRootPath)) {
            errors.add("Repository root path does not exist or is not a directory: " + repoRootPath);
        } else if (!Files.isRegularFile(repoRootPath.resolve("pom.xml"))) {
            errors.add("Missing required file: " + repoRootPath.resolve("pom.xml"));
        }

        if (connectorName == null || connectorName.trim().isEmpty()) {
            errors.add("Connector Name is required.");
        } else if (ServerTypeNormalizer.hasDisallowedCharacters(connectorName)) {
            errors.add("Connector Name may only contain lowercase letters and numbers, and must start with a letter.");
        }

        List<String> objectKindInputs = request != null ? request.getObjectKinds() : List.of();
        if (objectKindInputs == null || objectKindInputs.isEmpty()) {
            errors.add("At least one Connector Object is required.");
        }

        String artifactId = normalizeArtifactId(connectorName);
        if (artifactId.isEmpty()) {
            errors.add("Connector Name must contain at least one alphanumeric character.");
        }

        String packageName = artifactId;
        if (packageName.isEmpty() || !Character.isLetter(packageName.charAt(0))) {
            errors.add("Connector Name must start with a letter for a valid package name.");
        }

        String classPrefix = ServerTypeNormalizer.toPascalCase(artifactId);
        if (classPrefix.isEmpty() || !Character.isLetter(classPrefix.charAt(0))) {
            errors.add("Connector Name must start with a letter for a valid class name.");
        }

        if (!artifactId.isEmpty() && errors.isEmpty()
                && LegacyPlatformServerTypes.isBlockedForNewConnector(artifactId, repoRootPath)) {
            errors.add(LegacyPlatformServerTypes.generatorBlockedMessage(artifactId, repoRootPath));
        }

        List<ObjectKind> objectKinds = parseObjectKinds(objectKindInputs, errors);
        validateManifestAndReferences(request, errors);

        String primaryObjectOverride = request != null
                && request.getManifest() != null
                && request.getManifest().getConnectorMaster() != null
                ? request.getManifest().getConnectorMaster().getPrimaryObject()
                : null;
        String primaryObject = null;
        if (errors.isEmpty()) {
            try {
                primaryObject = PrimaryObjectResolver.resolve(objectKinds, primaryObjectOverride);
            } catch (IllegalArgumentException ex) {
                errors.add(ex.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new ConnectorGeneratorValidationException(errors);
        }

        return new ConnectorGenerationContext(connectorName.trim(), artifactId, packageName,
                classPrefix, artifactId, objectKinds, primaryObject);
    }

    private void validateManifestAndReferences(ConnectorGeneratorRequest request, List<String> errors) {
        ConnectorGeneratorRequest.ManifestInput manifest = request != null ? request.getManifest() : null;
        if (manifest == null) {
            errors.add("Manifest section is required.");
            return;
        }
        ConnectorGeneratorRequest.ConnectorMasterInput cm = manifest.getConnectorMaster();
        if (cm == null) {
            errors.add("Manifest.connectorMaster is required.");
        } else {
            if (isBlank(cm.getProtocol())) errors.add("Manifest.connectorMaster.protocol is required.");
            if (isBlank(cm.getOeConnCategory())) errors.add("Manifest.connectorMaster.oeConnCategory is required.");
            if (isBlank(cm.getUsageCostModel())) errors.add("Manifest.connectorMaster.usageCostModel is required.");
        }
        List<ConnectorGeneratorRequest.ReferenceInput> refs = request != null && request.getReferences() != null
                ? request.getReferences() : List.of();
        for (int i = 0; i < refs.size(); i++) {
            ConnectorGeneratorRequest.ReferenceInput r = refs.get(i);
            if (r == null) {
                errors.add("references[" + i + "] is invalid.");
                continue;
            }
            if (isBlank(r.getType())) {
                errors.add("references[" + i + "].type is required.");
            }
            if (isBlank(r.getUrl()) && isBlank(r.getText())) {
                errors.add("references[" + i + "] must contain at least one of url or text.");
            }
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private List<ObjectKind> parseObjectKinds(List<String> inputs, List<String> errors) {
        Set<ObjectKind> uniqueKinds = new LinkedHashSet<>();
        if (inputs != null) {
            for (String raw : inputs) {
                if (raw == null || raw.trim().isEmpty()) {
                    continue;
                }
                try {
                    uniqueKinds.add(ObjectKind.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    errors.add("Unsupported Connector Object: " + raw);
                }
            }
        }
        if (uniqueKinds.isEmpty()) {
            errors.add("At least one valid Connector Object is required.");
        }
        return new ArrayList<>(uniqueKinds);
    }

    private IconSpec validateIcon(MultipartFile icon) {
        if (icon == null || icon.isEmpty()) {
            throw new ConnectorGeneratorValidationException(List.of("Connector Icon is required."));
        }

        String originalExt = extractExtension(icon.getOriginalFilename());
        String contentType = icon.getContentType();

        String resolvedExt = "";
        if ("png".equals(originalExt)) resolvedExt = "png";
        else if ("jpg".equals(originalExt)) resolvedExt = "jpg";
        else if ("jpeg".equals(originalExt)) resolvedExt = "jpeg";
        else if ("svg".equals(originalExt)) resolvedExt = "svg";
        else if (contentType != null) {
            if (contentType.equalsIgnoreCase("image/png")) resolvedExt = "png";
            else if (contentType.equalsIgnoreCase("image/jpeg")) resolvedExt = "jpeg";
            else if (contentType.equalsIgnoreCase("image/jpg")) resolvedExt = "jpg";
            else if (contentType.equalsIgnoreCase("image/svg+xml")) resolvedExt = "svg";
        }

        if (resolvedExt.isEmpty()) {
            throw new ConnectorGeneratorValidationException(
                    List.of("Connector Icon must be PNG, JPG/JPEG, or SVG."));
        }

        // Extra safety: if the browser provided a contentType, ensure it's compatible with the chosen extension.
        if (contentType != null) {
            if (contentType.equalsIgnoreCase("image/png") && !resolvedExt.equals("png")) {
                throw new ConnectorGeneratorValidationException(
                        List.of("Connector Icon content type must be PNG."));
            }
            if (contentType.equalsIgnoreCase("image/svg+xml") && !resolvedExt.equals("svg")) {
                throw new ConnectorGeneratorValidationException(
                        List.of("Connector Icon content type must be SVG."));
            }
            if ((contentType.equalsIgnoreCase("image/jpeg") || contentType.equalsIgnoreCase("image/jpg"))
                    && !(resolvedExt.equals("jpg") || resolvedExt.equals("jpeg"))) {
                throw new ConnectorGeneratorValidationException(
                        List.of("Connector Icon content type must be JPG/JPEG."));
            }
        }

        if (icon.getSize() > ICON_MAX_BYTES) {
            throw new ConnectorGeneratorValidationException(
                    List.of("Connector Icon size must be less than 200 KB."));
        }

        if ("svg".equals(resolvedExt)) {
            // Basic SVG validation (skip 64x64/128x128 validation for SVG).
            try (InputStream inputStream = icon.getInputStream()) {
                byte[] head = inputStream.readNBytes(1024);
                String prefix = new String(head, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (!prefix.contains("<svg")) {
                    throw new ConnectorGeneratorValidationException(
                            List.of("Connector Icon must be a valid SVG file."));
                }
            } catch (IOException ex) {
                throw new RuntimeException("Failed to read Connector Icon", ex);
            }
            try {
                return new IconSpec(resolvedExt, icon.getBytes());
            } catch (IOException ex) {
                throw new RuntimeException("Failed to read Connector Icon bytes", ex);
            }
        }

        // Raster validation: ensure ImageIO can decode and dimensions match requirements.
        try (InputStream inputStream = icon.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new ConnectorGeneratorValidationException(
                        List.of("Connector Icon must be a valid PNG/JPG/JPEG image."));
            }
            int width = image.getWidth();
            int height = image.getHeight();
            boolean validSize = (width == ICON_SIZE_64 && height == ICON_SIZE_64)
                    || (width == ICON_SIZE_128 && height == ICON_SIZE_128);
            if (!validSize) {
                throw new ConnectorGeneratorValidationException(
                        List.of("Connector Icon dimensions must be 64x64 or 128x128 pixels."));
            }
            // Re-encode to ensure bytes match the chosen extension (prevents "rename but bytes mismatch").
            String format = ("png".equals(resolvedExt)) ? "png" : "jpeg";
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                boolean ok = ImageIO.write(image, format, out);
                if (!ok) {
                    throw new ConnectorGeneratorValidationException(
                            List.of("Connector Icon must be a valid PNG/JPG/JPEG image."));
                }
                byte[] encoded = out.toByteArray();
                if (encoded.length > ICON_MAX_BYTES) {
                    throw new ConnectorGeneratorValidationException(
                            List.of("Connector Icon size must be less than 200 KB."));
                }
                return new IconSpec(resolvedExt, encoded);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read Connector Icon", ex);
        }
    }

    /**
     * Resolves the Apps repository root used by {@link LegacyPlatformServerTypes} and
     * {@link SdkConnectorReactorScanner} when validating connector names.
     *
     * <p>When {@code repoRoot} is blank, uses {@code user.dir} if it contains a {@code pom.xml}, otherwise
     * its parent (typical when the JVM cwd is a submodule such as {@code csp-api}). Relative paths are resolved
     * against {@code user.dir}. Invalid paths fall back to {@code user.dir}.
     *
     * @param repoRoot optional path from the generator form or {@code GET /v1/generator/reserved-server-types}
     * @return absolute normalized path to the multi-module Apps root
     */
    public static Path resolveRepoRootPath(String repoRoot) {
        if (repoRoot == null || repoRoot.trim().isEmpty()) {
            return inferReactorRootFromWorkingDirectory();
        }
        try {
            Path raw = Paths.get(repoRoot.trim());
            Path resolved = raw.isAbsolute()
                    ? raw.normalize()
                    : Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().resolve(raw).normalize();
            if (looksLikeCspReactorRoot(resolved)) {
                return resolved;
            }
            Path inferred = inferReactorRootFrom(resolved);
            return inferred != null ? inferred : resolved;
        } catch (InvalidPathException ex) {
            return inferReactorRootFromWorkingDirectory();
        }
    }

    /**
     * Walks up from {@code user.dir} to locate the multi-module reactor root ({@code csp-api} + {@code assembly}).
     */
    private static Path inferReactorRootFromWorkingDirectory() {
        Path inferred = inferReactorRootFrom(
                Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize());
        return inferred != null
                ? inferred
                : Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }

    private static Path inferReactorRootFrom(Path start) {
        Path candidate = start;
        while (candidate != null) {
            if (looksLikeCspReactorRoot(candidate)) {
                return candidate.normalize();
            }
            candidate = candidate.getParent();
        }
        return null;
    }

    private static boolean looksLikeCspReactorRoot(Path dir) {
        return Files.isRegularFile(dir.resolve("pom.xml"))
                && Files.isRegularFile(dir.resolve("csp-api/pom.xml"))
                && Files.isRegularFile(dir.resolve("assembly/pom.xml"));
    }

    /**
     * Converts the user-facing connector name to the module artifact id and {@code serverType}.
     *
     * @param connectorName raw name from the generator form
     * @return canonical artifact id (empty when input has no alphanumeric characters)
     */
    private String normalizeArtifactId(String connectorName) {
        return ServerTypeNormalizer.normalize(connectorName);
    }

    private void writeCapabilityManifest(
            Path path,
            ConnectorGenerationContext context,
            ConnectorGeneratorRequest request,
            String iconExtension) throws IOException {
        ConnectorGeneratorRequest.ManifestInput manifest = request.getManifest();
        ConnectorGeneratorRequest.ConnectorMasterInput cm = manifest.getConnectorMaster();
        ConnectorGeneratorRequest.CrawlerSettingsInput cs = manifest.getCrawlerSettings() != null
                ? manifest.getCrawlerSettings()
                : new ConnectorGeneratorRequest.CrawlerSettingsInput();

        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> connectorMaster = new LinkedHashMap<>();
        connectorMaster.put("server", context.getArtifactId());
        connectorMaster.put("connectorName", context.getConnectorName());
        connectorMaster.put("tooltip", context.getConnectorName());
        connectorMaster.put("oeDocs", nullToEmpty(cm.getOeDocs()));
        connectorMaster.put("shortDescription", nullToEmpty(cm.getShortDescription()));
        connectorMaster.put("active", boolOrDefault(cm.getActive(), true));
        connectorMaster.put("connTypeId", 0);
        connectorMaster.put("connType", 20);
        connectorMaster.put("imgSource", "img/db/" + context.getArtifactId());
        connectorMaster.put("dialect", null);
        connectorMaster.put("protocol", cm.getProtocol());
        connectorMaster.put("dtoRegisterName", context.getConnectorFqcn());
        connectorMaster.put("accessDtoRegisterName", null);
        connectorMaster.put("driver", null);
        connectorMaster.put("oeConnCategory", cm.getOeConnCategory());
        connectorMaster.put("srcConnCategory", nullToEmpty(cm.getSrcConnCategory()));
        connectorMaster.put("conncategory", null);
        connectorMaster.put("artifactsPackage", "Standard");
        connectorMaster.put("version", null);
        connectorMaster.put("isBaseConnector", false);
        connectorMaster.put("crawling", boolOrDefault(cm.getCrawling(), true));
        connectorMaster.put("deltaCrawling", false);
        connectorMaster.put("queryLogsCrawling", false);
        connectorMaster.put("profiling", boolOrDefault(cm.getProfiling(), false));
        connectorMaster.put("deltaProfiling", false);
        connectorMaster.put("conditionalProfiling", false);
        // sampleProfiling set after crawlerOptions are normalized
        connectorMaster.put("querySheet", boolOrDefault(cm.getQuerySheet(), true));
        connectorMaster.put("queryPolicies", false);
        connectorMaster.put("querySheetAppPermissions", false);
        connectorMaster.put("querySheetSourcePermissions", false);
        connectorMaster.put("anomalyDetection", false);
        connectorMaster.put("dataAccess", boolOrDefault(cm.getDataAccess(), true));
        connectorMaster.put("dataAccessRemoteMaster", false);
        connectorMaster.put("dataAccessOvaledgeMaster", false);
        connectorMaster.put("autoLineage", boolOrDefault(cm.getAutoLineage(), false));
        connectorMaster.put("dataQuality", boolOrDefault(cm.getDataQuality(), false));
        connectorMaster.put("bridge", false);
        connectorMaster.put("proxy", false);
        connectorMaster.put("dnsField", null);
        connectorMaster.put("dataAtRestSecurity", true);
        connectorMaster.put("dataInTransitSecurity", true);
        connectorMaster.put("connectionPooling", true);
        connectorMaster.put("queryTimeout", false);
        connectorMaster.put("sourceSystemMetrics", false);
        connectorMaster.put("primaryObject", context.getPrimaryObject());
        connectorMaster.put("authenticationTypes",
                cm.getAuthenticationTypes() != null ? cm.getAuthenticationTypes() : List.of());
        connectorMaster.put("credentialManagers",
                cm.getCredentialManagers() != null && !cm.getCredentialManagers().isEmpty()
                        ? cm.getCredentialManagers() : List.of("DATABASE"));
        connectorMaster.put("usageCostModel", cm.getUsageCostModel());

        List<Map<String, String>> crawlerOptions =
                buildCrawlerOptions(context.getObjectKinds(), manifest.getCrawlerOptions());
        boolean profiling = boolOrDefault(cm.getProfiling(), false);
        if (profiling) {
            forceCrawlerOption(crawlerOptions, "CRAWLER_PREFERENCE", "P");
        } else {
            crawlerOptions.removeIf(o -> {
                String type = o.get("optionType");
                String key = o.get("optionKey");
                return "PROFILE_OPTIONS".equals(type)
                        || "PROFILE_TYPES".equals(type)
                        || ("CRAWLER_PREFERENCE".equals(type) && "P".equals(key));
            });
        }
        connectorMaster.put("sampleProfiling",
                profiling && hasCrawlerOption(crawlerOptions, "PROFILE_TYPES", "S"));
        root.put("connectorMaster", connectorMaster);

        Map<String, Object> crawlerSettings = buildCrawlerSettings(
                context.getConnectorName(),
                context.getArtifactId(),
                context.getObjectKinds(),
                crawlerOptions,
                cs);
        root.put("crawlerSettings", crawlerSettings);

        root.put("crawlerOptions", crawlerOptions);
        root.put("credentialManagerMappings",
                cm.getCredentialManagers() != null && !cm.getCredentialManagers().isEmpty()
                        ? cm.getCredentialManagers() : List.of("DATABASE"));
        root.put("icons", Map.of("icon", "icons/" + context.getArtifactId() + "." + iconExtension));

        Files.createDirectories(path.getParent());
        Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8);
    }

    private List<Map<String, String>> buildCrawlerOptions(
            List<ObjectKind> objectKinds,
            List<ConnectorGeneratorRequest.CrawlerOptionInput> requestedOptions) {
        Set<String> dedupe = new LinkedHashSet<>();
        List<Map<String, String>> options = new ArrayList<>();

        boolean hasRequested = requestedOptions != null && !requestedOptions.isEmpty();
        if (hasRequested) {
            for (ConnectorGeneratorRequest.CrawlerOptionInput option : requestedOptions) {
                if (option == null || isBlank(option.getOptionType()) || isBlank(option.getOptionKey())) {
                    continue;
                }
                addCrawlerOption(options, dedupe, option.getOptionType().trim(), option.getOptionKey().trim());
            }
        } else {
            // Mandatory defaults requested by user
            addCrawlerOption(options, dedupe, "CRAWLER_PREFERENCE", "S");
            addCrawlerOption(options, dedupe, "CRAWLER_PREFERENCE", "C");

            List<ObjectKind> kinds = objectKinds != null ? objectKinds : List.of();
            boolean hasTableLike = kinds.contains(ObjectKind.ENTITY) || kinds.contains(ObjectKind.VIEW);
            boolean hasReportLike = kinds.contains(ObjectKind.REPORT);
            boolean hasDatasets = kinds.contains(ObjectKind.DATASET);
            boolean hasFiles = kinds.contains(ObjectKind.FILE) || kinds.contains(ObjectKind.FILEFOLDERS);
            if (hasTableLike) addCrawlerOption(options, dedupe, "CRAWLER_OPTIONS", "TVC");
            if (hasReportLike) addCrawlerOption(options, dedupe, "CRAWLER_OPTIONS", "R");
            if (hasDatasets) addCrawlerOption(options, dedupe, "CRAWLER_OPTIONS", "DS");
            if (hasFiles) addCrawlerOption(options, dedupe, "CRAWLER_OPTIONS", "FF");
        }

        // Always keep required crawler preferences present
        addCrawlerOption(options, dedupe, "CRAWLER_PREFERENCE", "S");
        addCrawlerOption(options, dedupe, "CRAWLER_PREFERENCE", "C");
        return options;
    }

    private void addCrawlerOption(List<Map<String, String>> options, Set<String> dedupe, String type, String key) {
        String token = type + ":" + key;
        if (!dedupe.add(token)) return;
        Map<String, String> item = new LinkedHashMap<>();
        item.put("optionType", type);
        item.put("optionKey", key);
        options.add(item);
    }

    private void forceCrawlerOption(List<Map<String, String>> options, String type, String key) {
        Set<String> dedupe = new LinkedHashSet<>();
        for (Map<String, String> o : options) {
            if (o != null && o.get("optionType") != null && o.get("optionKey") != null) {
                dedupe.add(o.get("optionType") + ":" + o.get("optionKey"));
            }
        }
        addCrawlerOption(options, dedupe, type, key);
    }

    private Map<String, Object> buildCrawlerSettings(
            String connectorName,
            String server,
            List<ObjectKind> objectKinds,
            List<Map<String, String>> crawlerOptions,
            ConnectorGeneratorRequest.CrawlerSettingsInput overrides) {
        List<ObjectKind> kinds = objectKinds != null ? objectKinds : List.of();
        ConnectorGeneratorRequest.CrawlerSettingsInput cs =
                overrides != null ? overrides : new ConnectorGeneratorRequest.CrawlerSettingsInput();

        boolean hasTableLike = containsObjectKind(kinds, ObjectKind.ENTITY, ObjectKind.VIEW);
        boolean hasReport = kinds.contains(ObjectKind.REPORT);
        boolean hasDataset = kinds.contains(ObjectKind.DATASET);
        boolean hasFiles = containsObjectKind(kinds, ObjectKind.FILE, ObjectKind.FILEFOLDERS);
        boolean hasFunctionLike = containsObjectKind(kinds, ObjectKind.FUNCTION, ObjectKind.PROCEDURE);
        boolean hasIndex = kinds.contains(ObjectKind.INDEX);

        boolean tableviewncols = hasTableLike || hasCrawlerOption(crawlerOptions, "CRAWLER_OPTIONS", "TVC");
        boolean reports = hasReport || hasCrawlerOption(crawlerOptions, "CRAWLER_OPTIONS", "R");
        boolean reportcolumns = hasReport || hasCrawlerOption(crawlerOptions, "CRAWLER_OPTIONS", "RC");
        boolean datasets = hasDataset || hasCrawlerOption(crawlerOptions, "CRAWLER_OPTIONS", "DS");
        boolean fileFolders = hasFiles || hasCrawlerOption(crawlerOptions, "CRAWLER_OPTIONS", "FF");
        boolean relationship = hasCrawlerOption(crawlerOptions, "CRAWLER_OPTIONS", "RS");
        boolean procnfunc = hasFunctionLike || hasCrawlerOption(crawlerOptions, "CRAWLER_OPTIONS", "PF");
        boolean indexes = hasIndex || hasCrawlerOption(crawlerOptions, "CRAWLER_OPTIONS", "IN");
        boolean querypermissionmode = hasCrawlerOption(crawlerOptions, "CRAWLER_OPTIONS", "QP");
        boolean fullcrawl = !hasCrawlTypeOptions(crawlerOptions)
                || hasCrawlerOption(crawlerOptions, "CRAWL_TYPES", "FC");
        boolean incrementalcrawl = hasCrawlerOption(crawlerOptions, "CRAWL_TYPES", "INC");
        boolean profiletablesandcols = hasCrawlerOption(crawlerOptions, "PROFILE_OPTIONS", "TC");
        boolean profileviewsandcols = hasCrawlerOption(crawlerOptions, "PROFILE_OPTIONS", "VC");

        Map<String, Object> crawlerSettings = new LinkedHashMap<>();
        crawlerSettings.put("name", connectorName);
        crawlerSettings.put("server", server);
        crawlerSettings.put("connType", 0);
        crawlerSettings.put("tableviewncols", boolOrDefault(cs.getTableviewncols(), tableviewncols));
        crawlerSettings.put("relationship", boolOrDefault(cs.getRelationship(), relationship));
        crawlerSettings.put("procnfunc", boolOrDefault(cs.getProcnfunc(), procnfunc));
        crawlerSettings.put("reports", boolOrDefault(cs.getReports(), reports));
        crawlerSettings.put("reportcolumns", boolOrDefault(cs.getReportcolumns(), reportcolumns));
        crawlerSettings.put("querypermissionmode", boolOrDefault(cs.getQuerypermissionmode(), querypermissionmode));
        crawlerSettings.put("indexes", boolOrDefault(cs.getIndexes(), indexes));
        crawlerSettings.put("settings", boolOrDefault(cs.getSettings(), true));
        crawlerSettings.put("buildlineage", false);
        crawlerSettings.put("usernotification", true);
        crawlerSettings.put("contexturl", true);
        crawlerSettings.put("rdam", false);
        crawlerSettings.put("rbac", false);
        crawlerSettings.put("ubac", false);
        crawlerSettings.put("rpe", false);
        crawlerSettings.put("upe", false);
        crawlerSettings.put("uhp", false);
        crawlerSettings.put("une", false);
        crawlerSettings.put("rne", false);
        crawlerSettings.put("unemail", false);
        crawlerSettings.put("remotepolicy", false);
        crawlerSettings.put("fullcrawl", boolOrDefault(cs.getFullcrawl(), fullcrawl));
        crawlerSettings.put("incrementalcrawl", boolOrDefault(cs.getIncrementalcrawl(), incrementalcrawl));
        crawlerSettings.put("profiletablesandcols", boolOrDefault(cs.getProfiletablesandcols(), profiletablesandcols));
        crawlerSettings.put("profileviewsandcols", boolOrDefault(cs.getProfileviewsandcols(), profileviewsandcols));
        crawlerSettings.put("datasets", boolOrDefault(cs.getDatasets(), datasets));
        crawlerSettings.put("fileFolders", boolOrDefault(cs.getFileFolders(), fileFolders));
        return crawlerSettings;
    }

    private boolean containsObjectKind(List<ObjectKind> kinds, ObjectKind... candidates) {
        for (ObjectKind candidate : candidates) {
            if (kinds.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCrawlerOption(List<Map<String, String>> options, String optionType, String optionKey) {
        if (options == null || options.isEmpty()) {
            return false;
        }
        String type = optionType.trim().toUpperCase(Locale.ROOT);
        String key = optionKey.trim().toUpperCase(Locale.ROOT);
        for (Map<String, String> option : options) {
            if (option == null) {
                continue;
            }
            String actualType = option.get("optionType");
            String actualKey = option.get("optionKey");
            if (actualType != null
                    && actualKey != null
                    && type.equals(actualType.trim().toUpperCase(Locale.ROOT))
                    && key.equals(actualKey.trim().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCrawlTypeOptions(List<Map<String, String>> options) {
        if (options == null || options.isEmpty()) {
            return false;
        }
        for (Map<String, String> option : options) {
            if (option == null) {
                continue;
            }
            String actualType = option.get("optionType");
            if (actualType != null && "CRAWL_TYPES".equalsIgnoreCase(actualType.trim())) {
                return true;
            }
        }
        return false;
    }

    private void writeReferencesMarkdown(Path path, List<ConnectorGeneratorRequest.ReferenceInput> references) throws IOException {
        List<ConnectorGeneratorRequest.ReferenceInput> refs = references != null ? references : List.of();
        Map<String, List<ConnectorGeneratorRequest.ReferenceInput>> grouped = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ConnectorGeneratorRequest.ReferenceInput ref : refs) {
            if (ref == null) continue;
            String type = isBlank(ref.getType()) ? "Other" : ref.getType().trim();
            String normUrl = normalize(ref.getUrl());
            String normText = normalize(ref.getText());
            String dedupeKey = type.toLowerCase(Locale.ROOT) + "|" + normUrl + "|" + normText;
            if (!seen.add(dedupeKey)) continue;
            grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(ref);
        }

        StringBuilder md = new StringBuilder();
        md.append("# References\n\n");
        md.append("Generated from user-provided research inputs.\n\n");
        if (grouped.isEmpty()) {
            md.append("_No references supplied._\n");
        } else {
            md.append("## Index\n");
            for (String type : grouped.keySet()) {
                md.append("- [").append(type).append("](#").append(toAnchor(type)).append(")\n");
            }
            md.append("\n");
            for (Map.Entry<String, List<ConnectorGeneratorRequest.ReferenceInput>> e : grouped.entrySet()) {
                md.append("## ").append(e.getKey()).append("\n\n");
                int i = 1;
                for (ConnectorGeneratorRequest.ReferenceInput ref : e.getValue()) {
                    String title = !isBlank(ref.getTitle()) ? ref.getTitle().trim() : "Resource " + i;
                    md.append("### ").append(i).append(". ").append(title).append("\n");
                    if (!isBlank(ref.getUrl())) md.append("- URL: ").append(ref.getUrl().trim()).append("\n");
                    if (!isBlank(ref.getText())) {
                        md.append("- Notes:\n\n");
                        md.append(ref.getText().trim()).append("\n\n");
                    } else {
                        md.append("\n");
                    }
                    i++;
                }
            }
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, md.toString(), StandardCharsets.UTF_8);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String toAnchor(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s-]", "").trim().replaceAll("\\s+", "-");
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private boolean boolOrDefault(Boolean v, boolean d) {
        return v != null ? v : d;
    }

    private void writeGeneratedUnitTests(
            Path moduleRoot, ConnectorGenerationContext context, Map<String, String> templateValues,
            boolean profilingEnabled)
            throws IOException {
        Path unitTestBase =
                moduleRoot.resolve("src/test/java/com/ovaledge/csp/tests/unit/connector/" + context.getPackageName());
        writeTemplateIfAbsentWithInfo(
                unitTestBase.resolve(context.getClassPrefix() + "ConnectorUnitTest.java"),
                "archetype-resources/src/test/java/com/ovaledge/csp/tests/unit/connector/__packageName__/__classPrefix__ConnectorUnitTest.java",
                templateValues);
        writeTemplateIfAbsentWithInfo(
                unitTestBase.resolve(context.getClassPrefix() + "MetadataServiceUnitTest.java"),
                "archetype-resources/src/test/java/com/ovaledge/csp/tests/unit/connector/__packageName__/__classPrefix__MetadataServiceUnitTest.java",
                templateValues);
        writeTemplateIfAbsentWithInfo(
                unitTestBase.resolve(context.getClassPrefix() + "QueryServiceUnitTest.java"),
                "archetype-resources/src/test/java/com/ovaledge/csp/tests/unit/connector/__packageName__/__classPrefix__QueryServiceUnitTest.java",
                templateValues);
        if (profilingEnabled) {
            writeTemplateIfAbsentWithInfo(
                    unitTestBase.resolve(context.getClassPrefix() + "ProfilingServiceUnitTest.java"),
                    "archetype-resources/src/test/java/com/ovaledge/csp/tests/unit/connector/__packageName__/__classPrefix__ProfilingServiceUnitTest.java",
                    templateValues);
        }

        Path testRoot = moduleRoot.resolve("src/test/java/com/ovaledge/csp/tests");
        writeTemplateIfAbsentWithInfo(
                testRoot.resolve("unit/package-info.java"),
                "archetype-resources/src/test/java/com/ovaledge/csp/tests/unit/package-info.java",
                templateValues);
        writeTemplateIfAbsentWithInfo(
                testRoot.resolve("integration/package-info.java"),
                "archetype-resources/src/test/java/com/ovaledge/csp/tests/integration/package-info.java",
                templateValues);
        writeTemplateIfAbsentWithInfo(
                testRoot.resolve("deprecated/package-info.java"),
                "archetype-resources/src/test/java/com/ovaledge/csp/tests/deprecated/package-info.java",
                templateValues);
    }

    /**
     * Stamp generated module parent version from the repo POM, not stale
     * release properties ({@code TemplateValues} fallback).
     */
    private void applyRepoParentVersion(Map<String, String> templateValues, Path repoRoot) {
        if (templateValues == null || repoRoot == null) {
            return;
        }
        String version = readRepoProjectVersion(repoRoot);
        if (version != null && !version.isBlank()) {
            templateValues.put("sdkVersion", version);
        }
    }

    private void applyRepoParentVersion(Map<String, String> templateValues, String repoRoot) {
        if (repoRoot == null || repoRoot.isBlank()) {
            return;
        }
        try {
            applyRepoParentVersion(templateValues, Paths.get(repoRoot));
        } catch (InvalidPathException e) {
            log.debug("Could not resolve repo root for parent version: {}", e.getMessage());
        }
    }

    private String readRepoProjectVersion(Path repoRoot) {
        Path pom = repoRoot.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return null;
        }
        try {
            String text = Files.readString(pom, StandardCharsets.UTF_8);
            int parentEnd = text.indexOf("</parent>");
            String search = parentEnd >= 0 ? text.substring(parentEnd) : text;
            Matcher matcher = Pattern.compile("<version>\\s*([^<]+?)\\s*</version>").matcher(search);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (IOException e) {
            log.debug("Could not read repo parent version from {}: {}", pom, e.getMessage());
        }
        return null;
    }

    private boolean isProfilingEnabled(ConnectorGeneratorRequest request) {
        return request != null
                && request.getManifest() != null
                && request.getManifest().getConnectorMaster() != null
                && boolOrDefault(request.getManifest().getConnectorMaster().getProfiling(), false);
    }

    private String resolveProfilingMode(ConnectorGeneratorRequest request, boolean profilingEnabled) {
        if (!profilingEnabled) {
            return "SAMPLE";
        }
        String mode = request != null && request.getProfilingMode() != null
                ? request.getProfilingMode().trim().toUpperCase()
                : "SAMPLE";
        String protocol = null;
        if (request != null
                && request.getManifest() != null
                && request.getManifest().getConnectorMaster() != null) {
            protocol = request.getManifest().getConnectorMaster().getProtocol();
        }
        boolean jdbc = protocol != null && "JDBC".equalsIgnoreCase(protocol.trim());
        boolean hasFileKind = request != null
                && request.getObjectKinds() != null
                && request.getObjectKinds().stream()
                .filter(k -> k != null)
                .map(k -> k.trim().toUpperCase())
                .anyMatch(k -> "FILE".equals(k) || "FILEFOLDERS".equals(k));
        if ("DBMS".equals(mode) && (jdbc || hasFileKind)) {
            return "DBMS";
        }
        return "SAMPLE";
    }

    private void writeTemplate(Path path, String templateName, Map<String, String> values) throws IOException {
        Files.createDirectories(path.getParent());
        String template = readTemplate(templateName);
        String rendered = templateEngine.render(template, values);
        Files.writeString(path, rendered, StandardCharsets.UTF_8);
    }

    private void writeTemplateIfAbsentWithInfo(Path path, String templateName, Map<String, String> values) throws IOException {
        if (Files.exists(path)) {
            log.info("Skipping existing generated test file: {}", path);
            return;
        }
        writeTemplate(path, templateName, values);
    }

    private String readTemplate(String templateName) throws IOException {
        ClassPathResource resource = new ClassPathResource(templateName);
        if (resource.exists()) {
            try (InputStream inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        throw new FileNotFoundException("Template not found on classpath: " + templateName);
    }

    private byte[] zipDirectory(Path sourceDir) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOut = new ZipOutputStream(byteStream)) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relativePath = sourceDir.getParent().relativize(file);
                    ZipEntry zipEntry = new ZipEntry(relativePath.toString().replace("\\", "/"));
                    zipOut.putNextEntry(zipEntry);
                    zipOut.write(Files.readAllBytes(file));
                    zipOut.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return byteStream.toByteArray();
    }

    private void deleteDirectoryQuietly(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }
}
