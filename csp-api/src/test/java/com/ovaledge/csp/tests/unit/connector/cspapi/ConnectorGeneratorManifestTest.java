package com.ovaledge.csp.tests.unit.connector.cspapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ovaledge.csp.apps.app.generator.ConnectorGeneratorRequest;
import com.ovaledge.csp.apps.app.generator.ConnectorGeneratorResult;
import com.ovaledge.csp.apps.app.generator.ConnectorGeneratorService;
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

  @Test
  void generateZip_includesCspSdkUnitTestLayout(@TempDir Path tempDir) throws Exception {
    Path repoRoot = tempDir.resolve("repo");
    writeMinimalRepo(repoRoot);

    ConnectorGeneratorRequest request = buildRequest(List.of("ENTITY"), null);
    ConnectorGeneratorResult result = new ConnectorGeneratorService()
        .generate(request, pngIcon64x64());

    String connectorUnitTest =
        "democonnector/src/test/java/com/ovaledge/csp/tests/unit/connector/democonnector/DemoconnectorConnectorUnitTest.java";
    assertTrue(readTextFromZip(result.getZipBytes(), connectorUnitTest).contains("package com.ovaledge.csp.tests.unit.connector.democonnector"));
    assertTrue(readTextFromZip(result.getZipBytes(),
        "democonnector/src/test/java/com/ovaledge/csp/tests/unit/package-info.java").contains("com.ovaledge.csp.tests.unit"));
  }

  @Test
  void generateToDirectory_wiresPomsAtGeneratorMarkers(@TempDir Path repoRoot) throws Exception {
    writeMinimalRepoWithMarkers(repoRoot);

    ConnectorGeneratorRequest request = buildRequest(List.of("ENTITY"), null);
    request.setConnectorName("wiretest");
    request.setRepoRoot(repoRoot.toString());
    new ConnectorGeneratorService().generateToDirectory(request, pngIcon64x64(), repoRoot.toString());

    String parentPom = Files.readString(repoRoot.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertTrue(parentPom.contains("<module>wiretest</module>"));
    assertTrue(parentPom.indexOf("<module>wiretest</module>") < parentPom.indexOf(MODULE_MARKER));
    assertTrue(parentPom.contains("<artifactId>wiretest</artifactId>"));
    int depMarker = parentPom.indexOf(DEP_MARKER);
    int depMgmtClose = parentPom.indexOf("</dependencyManagement>");
    assertTrue(parentPom.indexOf("<artifactId>wiretest</artifactId>") < depMarker);
    assertTrue(depMarker < parentPom.indexOf("</dependencies>", depMarker));
    assertTrue(depMarker < depMgmtClose);

    String cspApiPom = Files.readString(repoRoot.resolve("csp-api/pom.xml"), StandardCharsets.UTF_8);
    assertTrue(cspApiPom.indexOf("<artifactId>wiretest</artifactId>") < cspApiPom.indexOf(DEP_MARKER));

    String assemblyPom = Files.readString(repoRoot.resolve("assembly/pom.xml"), StandardCharsets.UTF_8);
    assertTrue(assemblyPom.indexOf("<artifactId>wiretest</artifactId>") < assemblyPom.indexOf(DEP_MARKER));
    assertTrue(assemblyPom.indexOf("<artifactId>wiretest</artifactId>") < assemblyPom.indexOf("<!-- Compile dependency"));
    assertTrue(parentPom.contains("<csp.sdk.test.report.modules>assembly,csp-api,wiretest</csp.sdk.test.report.modules>"));
  }

  @Test
  void upsertTestReportModules_preservesWhitespace(@TempDir Path repoRoot) throws Exception {
    writeMinimalRepoWithWhitespace(repoRoot);

    ConnectorGeneratorRequest request = buildRequest(List.of("ENTITY"), null);
    request.setConnectorName("spacetest");
    request.setRepoRoot(repoRoot.toString());
    new ConnectorGeneratorService().generateToDirectory(request, pngIcon64x64(), repoRoot.toString());

    String parentPom = Files.readString(repoRoot.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertTrue(parentPom.contains("<csp.sdk.test.report.modules>  assembly,csp-api,spacetest  </csp.sdk.test.report.modules>"));
  }

  @Test
  void resolveRepoRootPath_infersFromCspApiSubdir(@TempDir Path repoRoot) throws Exception {
    Files.createDirectories(repoRoot.resolve("csp-api"));
    Files.createDirectories(repoRoot.resolve("assembly"));
    Files.writeString(repoRoot.resolve("pom.xml"), "<project/>", StandardCharsets.UTF_8);
    Files.writeString(repoRoot.resolve("csp-api/pom.xml"), "<project/>", StandardCharsets.UTF_8);
    Files.writeString(repoRoot.resolve("assembly/pom.xml"), "<project/>", StandardCharsets.UTF_8);

    Path inferred = ConnectorGeneratorService.resolveRepoRootPath(repoRoot.resolve("csp-api").toString());
    assertEquals(repoRoot.toAbsolutePath().normalize(), inferred.toAbsolutePath().normalize());
  }

  private static final String MODULE_MARKER =
      "<!-- Connector generator marker (modules): DO NOT REMOVE. New connector modules are inserted above this line. -->";
  private static final String DEP_MARKER =
      "<!-- Connector generator marker (dependencies): DO NOT REMOVE. New connector dependencies are inserted above this line. -->";

  private void writeMinimalRepoWithMarkers(Path repoRoot) throws IOException {
    Files.createDirectories(repoRoot.resolve("csp-api"));
    Files.createDirectories(repoRoot.resolve("assembly"));
    Files.writeString(repoRoot.resolve("pom.xml"), """
        <project>
          <modules>
            <module>existing</module>
        %s
          </modules>
          <properties>
            <csp.sdk.test.report.modules>assembly,csp-api</csp.sdk.test.report.modules>
          </properties>
          <dependencyManagement>
            <dependencies>
        %s
            </dependencies>
          </dependencyManagement>
        </project>
        """.formatted(MODULE_MARKER, DEP_MARKER), StandardCharsets.UTF_8);
    Files.writeString(repoRoot.resolve("csp-api/pom.xml"), """
        <project><dependencies>
        %s
        </dependencies></project>
        """.formatted(DEP_MARKER), StandardCharsets.UTF_8);
    Files.writeString(repoRoot.resolve("assembly/pom.xml"), """
        <project><dependencies>
        %s
        <!-- Compile dependency on csp-api -->
        </dependencies></project>
        """.formatted(DEP_MARKER), StandardCharsets.UTF_8);
  }

  private void writeMinimalRepoWithWhitespace(Path repoRoot) throws IOException {
    Files.createDirectories(repoRoot.resolve("csp-api"));
    Files.createDirectories(repoRoot.resolve("assembly"));
    Files.writeString(repoRoot.resolve("pom.xml"), """
        <project>
          <modules>
        %s
          </modules>
          <properties>
            <csp.sdk.test.report.modules>  assembly,csp-api  </csp.sdk.test.report.modules>
          </properties>
          <dependencyManagement>
            <dependencies>
        %s
            </dependencies>
          </dependencyManagement>
        </project>
        """.formatted(MODULE_MARKER, DEP_MARKER), StandardCharsets.UTF_8);
    Files.writeString(repoRoot.resolve("csp-api/pom.xml"), """
        <project><dependencies>
        %s
        </dependencies></project>
        """.formatted(DEP_MARKER), StandardCharsets.UTF_8);
    Files.writeString(repoRoot.resolve("assembly/pom.xml"), """
        <project><dependencies>
        %s
        </dependencies></project>
        """.formatted(DEP_MARKER), StandardCharsets.UTF_8);
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
    Files.writeString(repoRoot.resolve("pom.xml"), "<project><modules></modules><dependencyManagement><dependencies></dependencies></dependencyManagement></project>",
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
