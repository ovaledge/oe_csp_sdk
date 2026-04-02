package com.ovaledge.csp.apps.app.generator;

import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private final TemplateEngine templateEngine = new TemplateEngine();

    public ConnectorGeneratorResult generate(ConnectorGeneratorRequest request, MultipartFile icon) {
        ConnectorGenerationContext context = validateAndNormalize(request);
        validateIcon(icon);

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("connector-generator-");
            Path moduleRoot = tempDir.resolve(context.getArtifactId());
            Files.createDirectories(moduleRoot);

            Map<String, String> templateValues = TemplateValues.from(context);

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

            writeTemplate(
                    resourcesBase.resolve("configs/" + context.getServerType() + ".json"),
                    "archetype-resources/src/main/resources/configs/__artifactId__.json",
                    templateValues);

            if (icon != null && !icon.isEmpty()) {
                Path iconsPath = resourcesBase.resolve("icons");
                Files.createDirectories(iconsPath);
                Files.write(iconsPath.resolve(context.getArtifactId() + ".png"), icon.getBytes());
            } else {
                Path iconsPath = resourcesBase.resolve("icons");
                Files.createDirectories(iconsPath);
                Path iconPath = iconsPath.resolve(context.getArtifactId() + ".png");
                if (!writeDefaultIconFromResource(iconPath)) {
                    writeDefaultIcon(iconPath);
                }
            }

            writeTemplate(moduleRoot.resolve("INSTRUCTIONS.txt"),
                    "archetype-resources/INSTRUCTIONS.txt", templateValues);

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

        if (!errors.isEmpty()) {
            throw new ConnectorGeneratorValidationException(errors);
        }

        return new ConnectorGenerationContext(connectorName.trim(), artifactId, packageName,
                classPrefix, artifactId, objectKinds);
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

    private void validateIcon(MultipartFile icon) {
        if (icon == null || icon.isEmpty()) {
            return;
        }

        String contentType = icon.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("image/png")) {
            throw new ConnectorGeneratorValidationException(
                    List.of("Connector Icon must be a PNG file."));
        }

        if (icon.getSize() > ICON_MAX_BYTES) {
            throw new ConnectorGeneratorValidationException(
                    List.of("Connector Icon size must be less than 200 KB."));
        }

        try (InputStream inputStream = icon.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new ConnectorGeneratorValidationException(
                        List.of("Connector Icon must be a valid PNG image."));
            }
            int width = image.getWidth();
            int height = image.getHeight();
            boolean validSize = (width == ICON_SIZE_64 && height == ICON_SIZE_64)
                    || (width == ICON_SIZE_128 && height == ICON_SIZE_128);
            if (!validSize) {
                throw new ConnectorGeneratorValidationException(
                        List.of("Connector Icon dimensions must be 64x64 or 128x128 pixels."));
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read Connector Icon", ex);
        }
    }

    private void writeDefaultIcon(Path path) throws IOException {
        BufferedImage image = new BufferedImage(ICON_SIZE_64, ICON_SIZE_64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(0xE8, 0xEA, 0xF6));
        g2d.fillRoundRect(0, 0, ICON_SIZE_64, ICON_SIZE_64, 12, 12);
        g2d.setColor(new Color(0x66, 0x7E, 0xEA));
        g2d.drawRoundRect(2, 2, ICON_SIZE_64 - 4, ICON_SIZE_64 - 4, 12, 12);
        g2d.setColor(new Color(0x66, 0x7E, 0xEA));
        g2d.drawString("C", ICON_SIZE_64 / 2 - 4, ICON_SIZE_64 / 2 + 6);
        g2d.dispose();

        try (OutputStream out = Files.newOutputStream(path)) {
            ImageIO.write(image, "png", out);
        }
    }

    private boolean writeDefaultIconFromResource(Path path) {
        ClassPathResource resource = new ClassPathResource("templates/connector-generator/icon.png");
        if (!resource.exists()) {
            return false;
        }
        try (InputStream inputStream = resource.getInputStream()) {
            Files.copy(inputStream, path);
            return true;
        } catch (IOException ex) {
            return false;
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
