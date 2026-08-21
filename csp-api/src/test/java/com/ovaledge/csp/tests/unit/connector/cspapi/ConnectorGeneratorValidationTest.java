package com.ovaledge.csp.tests.unit.connector.cspapi;

import com.ovaledge.csp.apps.app.generator.ConnectorGeneratorRequest;
import com.ovaledge.csp.apps.app.generator.ConnectorGeneratorService;
import com.ovaledge.csp.apps.app.generator.ConnectorGeneratorValidationException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorGeneratorValidationTest {

    @Test
    void validateAndNormalize_invalidPrimaryObject_returnsValidationError(@TempDir Path repoRoot) throws Exception {
        writeMinimalRepo(repoRoot);
        ConnectorGeneratorRequest request = baseRequest(List.of("ENTITY"));
        request.getManifest().getConnectorMaster().setPrimaryObject("INVALID");

        ConnectorGeneratorValidationException ex = org.junit.jupiter.api.Assertions.assertThrows(
                ConnectorGeneratorValidationException.class,
                () -> new ConnectorGeneratorService().generateToDirectory(request, pngIcon64x64(), repoRoot.toString()));
        assertTrue(ex.getErrors().stream().anyMatch(msg -> msg.contains("Invalid primaryObject")));
    }

    @Test
    void validateAndNormalize_functionOnly_returnsValidationError(@TempDir Path repoRoot) throws Exception {
        writeMinimalRepo(repoRoot);
        ConnectorGeneratorRequest request = baseRequest(List.of("FUNCTION"));

        ConnectorGeneratorValidationException ex = org.junit.jupiter.api.Assertions.assertThrows(
                ConnectorGeneratorValidationException.class,
                () -> new ConnectorGeneratorService().generateToDirectory(request, pngIcon64x64(), repoRoot.toString()));
        assertTrue(ex.getErrors().stream().anyMatch(msg -> msg.contains("FUNCTION/PROCEDURE-only")));
    }

    private static ConnectorGeneratorRequest baseRequest(List<String> objectKinds) {
        ConnectorGeneratorRequest request = new ConnectorGeneratorRequest();
        request.setConnectorName("validconnector");
        request.setObjectKinds(objectKinds);
        ConnectorGeneratorRequest.ManifestInput manifest = new ConnectorGeneratorRequest.ManifestInput();
        ConnectorGeneratorRequest.ConnectorMasterInput cm = new ConnectorGeneratorRequest.ConnectorMasterInput();
        cm.setProtocol("REST");
        cm.setOeConnCategory("Application Connectors");
        cm.setUsageCostModel("Usage Based");
        manifest.setConnectorMaster(cm);
        request.setManifest(manifest);
        request.setReferences(List.of());
        return request;
    }

    private static void writeMinimalRepo(Path repoRoot) throws IOException {
        Files.createDirectories(repoRoot.resolve("csp-api"));
        Files.createDirectories(repoRoot.resolve("assembly"));
        Files.writeString(repoRoot.resolve("pom.xml"),
                "<project><modules></modules><dependencyManagement><dependencies></dependencies></dependencyManagement></project>",
                StandardCharsets.UTF_8);
        Files.writeString(repoRoot.resolve("csp-api/pom.xml"), "<project><dependencies></dependencies></project>",
                StandardCharsets.UTF_8);
        Files.writeString(repoRoot.resolve("assembly/pom.xml"), "<project><dependencies></dependencies></project>",
                StandardCharsets.UTF_8);
    }

    private static MultipartFile pngIcon64x64() throws IOException {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 64, 64);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        byte[] bytes = out.toByteArray();
        return new MultipartFile() {
            @Override public String getName() { return "icon"; }
            @Override public String getOriginalFilename() { return "icon.png"; }
            @Override public String getContentType() { return "image/png"; }
            @Override public boolean isEmpty() { return false; }
            @Override public long getSize() { return bytes.length; }
            @Override public byte[] getBytes() { return bytes; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
            @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), bytes); }
        };
    }
}
