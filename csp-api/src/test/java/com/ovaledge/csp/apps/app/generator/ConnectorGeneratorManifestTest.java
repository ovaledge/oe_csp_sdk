package com.ovaledge.csp.apps.app.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorGeneratorManifestTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void generateZip_manifestContainsConnectorNameAndPrimaryObject(@TempDir Path tempDir) throws Exception {
    Path repoRoot = tempDir.resolve("repo");
    writeMinimalRepo(repoRoot);

    ConnectorGeneratorRequest request = buildRequest(List.of("ENTITY"), null);
    ConnectorGeneratorResult result = new ConnectorGeneratorService()
        .generate(request, pngIcon64x64());

    JsonNode manifest = readManifestFromZip(result.getZipBytes(), "democonnector/src/main/resources/configs/democonnector.json");
    assertEquals("democonnector", manifest.path("connectorMaster").path("connectorName").asText());
    assertEquals("TVC", manifest.path("connectorMaster").path("primaryObject").asText());
    assertTrue(manifest.path("connectorMaster").path("displayName").isMissingNode());
  }

  @Test
  void generateZip_reportOnlyPrimaryObject(@TempDir Path tempDir) throws Exception {
    Path repoRoot = tempDir.resolve("repo");
    writeMinimalRepo(repoRoot);

    ConnectorGeneratorRequest request = buildRequest(List.of("REPORT"), null);
    ConnectorGeneratorResult result = new ConnectorGeneratorService()
        .generate(request, pngIcon64x64());

    JsonNode manifest = readManifestFromZip(result.getZipBytes(), "reportconnector/src/main/resources/configs/reportconnector.json");
    assertEquals("R", manifest.path("connectorMaster").path("primaryObject").asText());
    assertFalse(manifest.path("crawlerSettings").path("tableviewncols").asBoolean());
    assertTrue(manifest.path("crawlerSettings").path("reports").asBoolean());
    assertTrue(manifest.path("crawlerSettings").path("reportcolumns").asBoolean());

    String metadataService = readTextFromZip(
        result.getZipBytes(),
        "reportconnector/src/main/java/com/ovaledge/csp/apps/reportconnector/main/ReportconnectorMetadataService.java");
    assertTrue(metadataService.contains("GENERATOR:START supported-objects"));
    assertTrue(metadataService.contains("ObjectKind.REPORT.value()"));
    assertFalse(metadataService.contains("ObjectKind.ENTITY.value()"));
  }

  @Test
  void generateZip_datasetOnly_setsDatasetsFlag(@TempDir Path tempDir) throws Exception {
    Path repoRoot = tempDir.resolve("repo");
    writeMinimalRepo(repoRoot);

    ConnectorGeneratorRequest request = buildRequest(List.of("DATASET"), null);
    request.setConnectorName("etlconnector");
    ConnectorGeneratorResult result = new ConnectorGeneratorService()
        .generate(request, pngIcon64x64());

    JsonNode manifest = readManifestFromZip(result.getZipBytes(), "etlconnector/src/main/resources/configs/etlconnector.json");
    assertEquals("DS", manifest.path("connectorMaster").path("primaryObject").asText());
    assertFalse(manifest.path("crawlerSettings").path("tableviewncols").asBoolean());
    assertTrue(manifest.path("crawlerSettings").path("datasets").asBoolean());
  }

  @Test
  void generateToDirectory_manifestContainsPrimaryObject(@TempDir Path repoRoot) throws Exception {
    writeMinimalRepo(repoRoot);

    ConnectorGeneratorRequest request = buildRequest(List.of("FILEFOLDERS"), "FF");
    request.setRepoRoot(repoRoot.toString());
    new ConnectorGeneratorService().generateToDirectory(request, pngIcon64x64(), repoRoot.toString());

    Path manifestPath = repoRoot.resolve("filesconnector/src/main/resources/configs/filesconnector.json");
    JsonNode manifest = objectMapper.readTree(Files.readString(manifestPath, StandardCharsets.UTF_8));
    assertEquals("filesconnector", manifest.path("connectorMaster").path("connectorName").asText());
    assertEquals("FF", manifest.path("connectorMaster").path("primaryObject").asText());
  }

  private ConnectorGeneratorRequest buildRequest(List<String> objectKinds, String primaryObjectOverride) {
    ConnectorGeneratorRequest request = new ConnectorGeneratorRequest();
    String connectorName = objectKinds.contains("REPORT") ? "reportconnector"
        : objectKinds.contains("FILEFOLDERS") ? "filesconnector" : "democonnector";
    request.setConnectorName(connectorName);
    request.setObjectKinds(objectKinds);

    ConnectorGeneratorRequest.ManifestInput manifest = new ConnectorGeneratorRequest.ManifestInput();
    ConnectorGeneratorRequest.ConnectorMasterInput cm = new ConnectorGeneratorRequest.ConnectorMasterInput();
    cm.setProtocol("REST");
    cm.setOeConnCategory("Application Connectors");
    cm.setUsageCostModel("Usage Based");
    if (primaryObjectOverride != null) {
      cm.setPrimaryObject(primaryObjectOverride);
    }
    manifest.setConnectorMaster(cm);
    request.setManifest(manifest);
    request.setReferences(List.of());
    return request;
  }

  private void writeMinimalRepo(Path repoRoot) throws IOException {
    Files.createDirectories(repoRoot.resolve("csp-api"));
    Files.createDirectories(repoRoot.resolve("assembly"));
    Files.writeString(repoRoot.resolve("pom.xml"), "<project><modules></modules><dependencyManagement></dependencyManagement></project>",
        StandardCharsets.UTF_8);
    Files.writeString(repoRoot.resolve("csp-api/pom.xml"), "<project><dependencies></dependencies></project>",
        StandardCharsets.UTF_8);
    Files.writeString(repoRoot.resolve("assembly/pom.xml"), "<project><dependencies></dependencies></project>",
        StandardCharsets.UTF_8);
  }

  private JsonNode readManifestFromZip(byte[] zipBytes, String entryName) throws IOException {
    return objectMapper.readTree(readTextFromZip(zipBytes, entryName).getBytes(StandardCharsets.UTF_8));
  }

  private String readTextFromZip(byte[] zipBytes, String entryName) throws IOException {
    try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zipIn.getNextEntry()) != null) {
        if (entryName.equals(entry.getName())) {
          return new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
        }
      }
    }
    throw new IOException("Missing zip entry: " + entryName);
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
