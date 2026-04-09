package com.ovaledge.csp.apps.app.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ConnectorGeneratorService {

    private static final int ICON_MAX_BYTES = 200 * 1024;
    private static final int ICON_SIZE_64 = 64;
    private static final int ICON_SIZE_128 = 128;
    private static final String MODULE_MARKER_NEW =
            "<!-- Connector generator marker (modules): DO NOT REMOVE. New connector modules are inserted above this line. -->";
    private static final String DEP_MARKER_NEW =
            "<!-- Connector generator marker (dependencies): DO NOT REMOVE. New connector dependencies are inserted above this line. -->";

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
        ConnectorGenerationContext context = validateAndNormalize(request);
        IconSpec iconSpec = validateIcon(icon);
        String iconExtension = iconSpec.extension();

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("connector-generator-");
            Path moduleRoot = tempDir.resolve(context.getArtifactId());
            Files.createDirectories(moduleRoot);

            Map<String, String> templateValues = TemplateValues.from(context);
            templateValues.put("iconExtension", iconExtension);

            // Use connector-archetype templates as the single source of truth
            writeTemplate(moduleRoot.resolve("pom.xml"), "archetype-resources/pom.xml", templateValues);
            writeTemplate(moduleRoot.resolve("INSTRUCTIONS.txt"), "archetype-resources/INSTRUCTIONS.txt", templateValues);

            Path javaBase = moduleRoot.resolve("src/main/java/com/ovaledge/csp/apps/" + context.getPackageName());
            writeTemplate(javaBase.resolve("main/" + context.getClassPrefix() + "Connector.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__artifactId__/main/__classPrefix__Connector.java",
                    templateValues);
            writeTemplate(javaBase.resolve("main/" + context.getClassPrefix() + "MetadataService.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__artifactId__/main/__classPrefix__MetadataService.java",
                    templateValues);
            writeTemplate(javaBase.resolve("main/" + context.getClassPrefix() + "QueryService.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__artifactId__/main/__classPrefix__QueryService.java",
                    templateValues);
            writeTemplate(javaBase.resolve("constants/" + context.getClassPrefix() + "Constants.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__artifactId__/constants/__classPrefix__Constants.java",
                    templateValues);
            writeTemplate(javaBase.resolve("quick/" + context.getClassPrefix() + "Controller.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__artifactId__/quick/__classPrefix__Controller.java",
                    templateValues);
            writeTemplate(javaBase.resolve("quick/" + context.getClassPrefix() + "QuickApplication.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__artifactId__/quick/__classPrefix__QuickApplication.java",
                    templateValues);

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
        ConnectorGenerationContext context = validateAndNormalize(request);
        IconSpec iconSpec = validateIcon(icon);
        String iconExtension = iconSpec.extension();

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
            templateValues.put("iconExtension", iconExtension);

            // Use connector-archetype templates as the single source of truth
            writeTemplate(moduleRoot.resolve("pom.xml"), "archetype-resources/pom.xml", templateValues);

            Path javaBase = moduleRoot.resolve("src/main/java/com/ovaledge/csp/apps/" + context.getPackageName());
            writeTemplate(javaBase.resolve("main/" + context.getClassPrefix() + "Connector.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__artifactId__/main/__classPrefix__Connector.java",
                    templateValues);
            writeTemplate(javaBase.resolve("main/" + context.getClassPrefix() + "MetadataService.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__artifactId__/main/__classPrefix__MetadataService.java",
                    templateValues);
            writeTemplate(javaBase.resolve("main/" + context.getClassPrefix() + "QueryService.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__artifactId__/main/__classPrefix__QueryService.java",
                    templateValues);
            writeTemplate(javaBase.resolve("constants/" + context.getClassPrefix() + "Constants.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__artifactId__/constants/__classPrefix__Constants.java",
                    templateValues);
            writeTemplate(javaBase.resolve("quick/" + context.getClassPrefix() + "Controller.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__artifactId__/quick/__classPrefix__Controller.java",
                    templateValues);
            writeTemplate(javaBase.resolve("quick/" + context.getClassPrefix() + "QuickApplication.java"),
                    "archetype-resources/src/main/java/com/ovaledge/csp/apps/__artifactId__/quick/__classPrefix__QuickApplication.java",
                    templateValues);

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

            boolean parentModuleAdded = upsertParentModule(parentPomPath, context.getArtifactId());
            boolean parentDependencyManagementAdded = upsertParentDependencyManagement(parentPomPath, context.getArtifactId());
            boolean cspApiDependencyAdded = upsertModuleDependency(cspApiPomPath, context.getArtifactId());
            boolean assemblyDependencyAdded = upsertModuleDependency(assemblyPomPath, context.getArtifactId());

            Map<String, String> response = new LinkedHashMap<>();
            response.put("artifactId", context.getArtifactId());
            response.put("generatedPath", moduleRoot.toString());
            response.put("wiringApplied", "true");
            response.put("parentModuleAdded", String.valueOf(parentModuleAdded));
            response.put("parentDependencyManagementAdded", String.valueOf(parentDependencyManagementAdded));
            response.put("cspApiDependencyAdded", String.valueOf(cspApiDependencyAdded));
            response.put("assemblyDependencyAdded", String.valueOf(assemblyDependencyAdded));
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
            return response;
        } catch (IOException e) {
            // Best-effort cleanup if generation partially succeeded.
            deleteDirectoryQuietly(moduleRoot);
            throw new RuntimeException("Failed to generate connector module", e);
        }
    }

    private boolean upsertParentModule(Path parentPomPath, String artifactId) throws IOException {
        String pom = Files.readString(parentPomPath, StandardCharsets.UTF_8);
        String marker = firstMarkerPresent(pom, MODULE_MARKER_NEW);
        String moduleLine = "    <module>" + artifactId + "</module>";
        if (pom.contains("<module>" + artifactId + "</module>")) {
            return false;
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
        Files.writeString(parentPomPath, updated, StandardCharsets.UTF_8);
        return true;
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

    private boolean upsertParentDependencyManagement(Path parentPomPath, String artifactId) throws IOException {
        String pom = Files.readString(parentPomPath, StandardCharsets.UTF_8);
        String marker = firstMarkerPresent(pom, DEP_MARKER_NEW);
        String depSnippet = "      <dependency>\n"
                + "        <groupId>com.ovaledge</groupId>\n"
                + "        <artifactId>" + artifactId + "</artifactId>\n"
                + "        <version>${project.version}</version>\n"
                + "      </dependency>";
        if (pom.contains("<artifactId>" + artifactId + "</artifactId>")) {
            return false;
        }
        String updated;
        if (marker != null) {
            updated = pom.replace(marker, depSnippet + "\n" + marker);
        } else {
            updated = insertBeforeClosingTagInSection(
                    pom,
                    "<dependencyManagement>",
                    "</dependencyManagement>",
                    depSnippet + "\n",
                    "parent pom.xml dependencyManagement section");
        }
        Files.writeString(parentPomPath, updated, StandardCharsets.UTF_8);
        return true;
    }

    private boolean upsertModuleDependency(Path pomPath, String artifactId) throws IOException {
        String pom = Files.readString(pomPath, StandardCharsets.UTF_8);
        String marker = firstMarkerPresent(pom, DEP_MARKER_NEW);
        String depSnippet = "        <dependency>\n"
                + "            <groupId>com.ovaledge</groupId>\n"
                + "            <artifactId>" + artifactId + "</artifactId>\n"
                + "        </dependency>";
        if (pom.contains("<artifactId>" + artifactId + "</artifactId>")) {
            return false;
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
                    pomPath + " dependencies section");
        }
        Files.writeString(pomPath, updated, StandardCharsets.UTF_8);
        return true;
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
        int sectionStart = xml.indexOf(sectionOpenTag);
        if (sectionStart < 0) {
            throw new IOException("Unable to locate " + sectionLabel + " (missing " + sectionOpenTag + ").");
        }
        int sectionEnd = xml.indexOf(sectionCloseTag, sectionStart);
        if (sectionEnd < 0) {
            throw new IOException("Unable to locate " + sectionLabel + " (missing " + sectionCloseTag + ").");
        }
        return xml.substring(0, sectionEnd) + snippetWithTrailingNewline + xml.substring(sectionEnd);
    }

    private ConnectorGenerationContext validateAndNormalize(ConnectorGeneratorRequest request) {
        List<String> errors = new ArrayList<>();
        String connectorName = request != null ? request.getConnectorName() : null;

        if (connectorName == null || connectorName.trim().isEmpty()) {
            errors.add("Connector Name is required.");
        }

        List<String> objectKindInputs = request != null ? request.getObjectKinds() : List.of();
        if (objectKindInputs == null || objectKindInputs.isEmpty()) {
            errors.add("At least one Connector Object is required.");
        }

        String artifactId = normalizeArtifactId(connectorName);
        if (artifactId.isEmpty()) {
            errors.add("Connector Name must contain at least one alphanumeric character.");
        }

        String packageName = artifactId.replace("-", "");
        if (packageName.isEmpty() || !Character.isLetter(packageName.charAt(0))) {
            errors.add("Connector Name must start with a letter for a valid package name.");
        }

        String classPrefix = buildClassPrefix(artifactId);
        if (classPrefix.isEmpty() || !Character.isLetter(classPrefix.charAt(0))) {
            errors.add("Connector Name must start with a letter for a valid class name.");
        }

        List<ObjectKind> objectKinds = parseObjectKinds(objectKindInputs, errors);
        validateManifestAndReferences(request, errors);

        if (!errors.isEmpty()) {
            throw new ConnectorGeneratorValidationException(errors);
        }

        return new ConnectorGenerationContext(connectorName.trim(), artifactId, packageName,
                classPrefix, artifactId, objectKinds);
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

    private String normalizeArtifactId(String connectorName) {
        if (connectorName == null) {
            return "";
        }
        String lower = connectorName.trim().toLowerCase(Locale.ROOT);
        String cleaned = lower.replaceAll("[^a-z0-9\\s-]", " ");
        String hyphenated = cleaned.trim().replaceAll("\\s+", "-").replaceAll("-+", "-");
        return hyphenated.replaceAll("^-|-$", "");
    }

    private String buildClassPrefix(String artifactId) {
        if (artifactId == null || artifactId.isEmpty()) {
            return "";
        }
        String[] parts = artifactId.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
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
        connectorMaster.put("displayName", context.getConnectorName());
        connectorMaster.put("tooltip", context.getConnectorName());
        connectorMaster.put("oeDocs", nullToEmpty(cm.getOeDocs()));
        connectorMaster.put("shortDescription", nullToEmpty(cm.getShortDescription()));
        connectorMaster.put("active", boolOrDefault(cm.getActive(), true));
        connectorMaster.put("connTypeId", 0);
        connectorMaster.put("connType", 20);
        connectorMaster.put("imgSource", "img/db/" + context.getArtifactId());
        connectorMaster.put("dialect", null);
        connectorMaster.put("protocol", cm.getProtocol());
        connectorMaster.put("dtoRegisterName",
                "com.ovaledge.csp.apps." + context.getArtifactId() + ".main." + context.getClassPrefix() + "Connector");
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
        connectorMaster.put("sampleProfiling", false);
        connectorMaster.put("conditionalProfiling", false);
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
        connectorMaster.put("authenticationTypes",
                cm.getAuthenticationTypes() != null ? cm.getAuthenticationTypes() : List.of());
        connectorMaster.put("credentialManagers",
                cm.getCredentialManagers() != null && !cm.getCredentialManagers().isEmpty()
                        ? cm.getCredentialManagers() : List.of("DATABASE"));
        connectorMaster.put("usageCostModel", cm.getUsageCostModel());
        root.put("connectorMaster", connectorMaster);

        Map<String, Object> crawlerSettings = new LinkedHashMap<>();
        crawlerSettings.put("name", context.getConnectorName());
        crawlerSettings.put("server", context.getArtifactId());
        crawlerSettings.put("connType", 0);
        crawlerSettings.put("tableviewncols", true);
        crawlerSettings.put("relationship", boolOrDefault(cs.getRelationship(), false));
        crawlerSettings.put("procnfunc", boolOrDefault(cs.getProcnfunc(), false));
        crawlerSettings.put("reports", boolOrDefault(cs.getReports(), false));
        crawlerSettings.put("reportcolumns", boolOrDefault(cs.getReportcolumns(), false));
        crawlerSettings.put("querypermissionmode", false);
        crawlerSettings.put("indexes", boolOrDefault(cs.getIndexes(), false));
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
        crawlerSettings.put("fullcrawl", boolOrDefault(cs.getFullcrawl(), true));
        crawlerSettings.put("incrementalcrawl", boolOrDefault(cs.getIncrementalcrawl(), false));
        crawlerSettings.put("profiletablesandcols", false);
        crawlerSettings.put("profileviewsandcols", false);
        root.put("crawlerSettings", crawlerSettings);

        root.put("crawlerOptions", buildCrawlerOptions(context.getObjectKinds(), manifest.getCrawlerOptions()));
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
            boolean hasFiles = kinds.contains(ObjectKind.FILE) || kinds.contains(ObjectKind.FILEFOLDERS);
            if (hasTableLike) addCrawlerOption(options, dedupe, "CRAWLER_OPTIONS", "TVC");
            if (hasReportLike) addCrawlerOption(options, dedupe, "CRAWLER_OPTIONS", "RS");
            if (hasFiles) addCrawlerOption(options, dedupe, "CRAWLER_OPTIONS", "FS");
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

    private void writeTemplate(Path path, String templateName, Map<String, String> values) throws IOException {
        Files.createDirectories(path.getParent());
        String template = readTemplate(templateName);
        String rendered = templateEngine.render(template, values);
        Files.writeString(path, rendered, StandardCharsets.UTF_8);
    }

    private String readTemplate(String templateName) throws IOException {
        ClassPathResource resource = new ClassPathResource(templateName);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
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
