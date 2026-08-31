package com.ovaledge.csp.tests.unit.connector.cspapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.ovaledge.csp.apps.app.generator.ConnectorGeneratorRequest;
import com.ovaledge.csp.apps.app.generator.ConnectorGeneratorResult;
import com.ovaledge.csp.apps.app.generator.ConnectorGeneratorService;
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

  @Test
  void generateZip_includesCspAppsUnitTestLayout(@TempDir Path tempDir) throws Exception {
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
  void generateZip_profilingOff_omitsProfilingArtifacts(@TempDir Path tempDir) throws Exception {
    Path repoRoot = tempDir.resolve("repo");
    writeMinimalRepo(repoRoot);

    ConnectorGeneratorRequest request = buildRequest(List.of("ENTITY"), null);
    request.getManifest().getConnectorMaster().setProfiling(false);
    ConnectorGeneratorResult result = new ConnectorGeneratorService()
        .generate(request, pngIcon64x64());

    JsonNode manifest = readManifestFromZip(result.getZipBytes(),
        "democonnector/src/main/resources/configs/democonnector.json");
    assertFalse(manifest.path("connectorMaster").path("profiling").asBoolean());
    assertFalse(manifest.path("connectorMaster").path("sampleProfiling").asBoolean());
    assertFalse(manifest.path("crawlerSettings").path("profiletablesandcols").asBoolean());
    assertFalse(manifest.path("crawlerSettings").path("profileviewsandcols").asBoolean());
    assertFalse(hasCrawlerOption(manifest, "CRAWLER_PREFERENCE", "P"));
    assertFalse(hasCrawlerOption(manifest, "PROFILE_OPTIONS", "TC"));
    assertFalse(hasCrawlerOption(manifest, "PROFILE_OPTIONS", "VC"));
    assertFalse(hasCrawlerOption(manifest, "PROFILE_TYPES", "S"));
    assertFalse(zipContains(result.getZipBytes(),
        "democonnector/src/main/java/com/ovaledge/csp/apps/democonnector/main/DemoconnectorProfilingService.java"));
    String connector = readTextFromZip(result.getZipBytes(),
        "democonnector/src/main/java/com/ovaledge/csp/apps/democonnector/main/DemoconnectorConnector.java");
    assertFalse(connector.contains("getProfilingService"));
  }

  @Test
  void generateZip_profilingDbmsAutoOnly_sampleProfilingFalseWithoutD(@TempDir Path tempDir) throws Exception {
    Path repoRoot = tempDir.resolve("repo");
    writeMinimalRepo(repoRoot);

    ConnectorGeneratorRequest request = buildProfilingRequest("DBMS", "JDBC",
        List.of(
            option("CRAWLER_PREFERENCE", "S"),
            option("CRAWLER_PREFERENCE", "C"),
            option("PROFILE_TYPES", "A")),
        "profileauto");
    ConnectorGeneratorResult result = new ConnectorGeneratorService()
        .generate(request, pngIcon64x64());

    JsonNode manifest = readManifestFromZip(result.getZipBytes(),
        "profileauto/src/main/resources/configs/profileauto.json");
    assertTrue(manifest.path("connectorMaster").path("profiling").asBoolean());
    assertFalse(manifest.path("connectorMaster").path("sampleProfiling").asBoolean());
    assertTrue(hasCrawlerOption(manifest, "CRAWLER_PREFERENCE", "P"));
    assertTrue(hasCrawlerOption(manifest, "PROFILE_TYPES", "A"));
    assertFalse(hasCrawlerOption(manifest, "PROFILE_TYPES", "S"));
    assertFalse(hasCrawlerOption(manifest, "PROFILE_TYPES", "D"));
  }

  @Test
  void generateZip_profilingDbms_emitsServiceAndOasisOptions(@TempDir Path tempDir) throws Exception {
    Path repoRoot = tempDir.resolve("repo");
    writeMinimalRepo(repoRoot);

    ConnectorGeneratorRequest request = buildProfilingRequest("DBMS", "JDBC",
        List.of(
            option("CRAWLER_PREFERENCE", "S"),
            option("CRAWLER_PREFERENCE", "C"),
            option("PROFILE_OPTIONS", "TC"),
            option("PROFILE_TYPES", "A"),
            option("PROFILE_TYPES", "S"),
            option("PROFILE_TYPES", "Q"),
            option("PROFILE_TYPES", "D")));
    ConnectorGeneratorResult result = new ConnectorGeneratorService()
        .generate(request, pngIcon64x64());

    JsonNode manifest = readManifestFromZip(result.getZipBytes(),
        "profiledbms/src/main/resources/configs/profiledbms.json");
    assertTrue(manifest.path("connectorMaster").path("profiling").asBoolean());
    assertTrue(manifest.path("connectorMaster").path("sampleProfiling").asBoolean());
    assertTrue(manifest.path("crawlerSettings").path("profiletablesandcols").asBoolean());
    assertTrue(hasCrawlerOption(manifest, "CRAWLER_PREFERENCE", "P"));
    assertTrue(hasCrawlerOption(manifest, "PROFILE_TYPES", "A"));
    assertTrue(hasCrawlerOption(manifest, "PROFILE_TYPES", "S"));
    assertTrue(hasCrawlerOption(manifest, "PROFILE_TYPES", "Q"));
    assertTrue(hasCrawlerOption(manifest, "PROFILE_TYPES", "D"));

    String profilingService = readTextFromZip(result.getZipBytes(),
        "profiledbms/src/main/java/com/ovaledge/csp/apps/profiledbms/main/ProfiledbmsProfilingService.java");
    assertTrue(profilingService.contains("getRowCount"));
    assertTrue(profilingService.contains("profileColumn"));
    assertTrue(profilingService.contains("sampleProfile"));

    String connector = readTextFromZip(result.getZipBytes(),
        "profiledbms/src/main/java/com/ovaledge/csp/apps/profiledbms/main/ProfiledbmsConnector.java");
    assertTrue(connector.contains("getProfilingService"));

    assertTrue(zipContains(result.getZipBytes(),
        "profiledbms/src/test/java/com/ovaledge/csp/tests/unit/connector/profiledbms/ProfiledbmsProfilingServiceUnitTest.java"));
  }

  @Test
  void generateZip_profilingSampleOnly_emitsSampleStub(@TempDir Path tempDir) throws Exception {
    Path repoRoot = tempDir.resolve("repo");
    writeMinimalRepo(repoRoot);

    ConnectorGeneratorRequest request = buildProfilingRequest("SAMPLE", "REST",
        List.of(
            option("CRAWLER_PREFERENCE", "S"),
            option("CRAWLER_PREFERENCE", "C"),
            option("PROFILE_TYPES", "S"),
            option("PROFILE_TYPES", "D")));
    ConnectorGeneratorResult result = new ConnectorGeneratorService()
        .generate(request, pngIcon64x64());

    JsonNode manifest = readManifestFromZip(result.getZipBytes(),
        "profilesample/src/main/resources/configs/profilesample.json");
    assertTrue(manifest.path("connectorMaster").path("profiling").asBoolean());
    assertTrue(manifest.path("connectorMaster").path("sampleProfiling").asBoolean());
    assertTrue(hasCrawlerOption(manifest, "CRAWLER_PREFERENCE", "P"));
    assertTrue(hasCrawlerOption(manifest, "PROFILE_TYPES", "S"));
    assertFalse(hasCrawlerOption(manifest, "PROFILE_TYPES", "A"));
    assertTrue(hasCrawlerOption(manifest, "PROFILE_TYPES", "D"));

    String profilingService = readTextFromZip(result.getZipBytes(),
        "profilesample/src/main/java/com/ovaledge/csp/apps/profilesample/main/ProfilesampleProfilingService.java");
    assertTrue(profilingService.contains("sampleProfile"));
    assertTrue(profilingService.contains("public long getRowCount"));
    assertFalse(profilingService.contains("public ProfileColumnResult profileColumn"));

    String profilingUnitTest = readTextFromZip(result.getZipBytes(),
        "profilesample/src/test/java/com/ovaledge/csp/tests/unit/connector/profilesample/ProfilesampleProfilingServiceUnitTest.java");
    assertTrue(profilingUnitTest.contains("getRowCount_returnsZeroForStub"));
    assertFalse(profilingUnitTest.contains("getRowCount_throwsUnsupportedByDefault"));
  }

  @Test
  void generateZip_profilingFile_emitsProfileFileStub(@TempDir Path tempDir) throws Exception {
    Path repoRoot = tempDir.resolve("repo");
    writeMinimalRepo(repoRoot);

    // Start with an existing profiling request and then swap selected object kind to FILE.
    ConnectorGeneratorRequest request = buildProfilingRequest("SAMPLE", "REST",
        List.of(
            option("CRAWLER_PREFERENCE", "S"),
            option("PROFILE_TYPES", "D")));
    request.setObjectKinds(List.of("FILE"));

    ConnectorGeneratorResult result = new ConnectorGeneratorService()
        .generate(request, pngIcon64x64());

    String profilingService = readTextFromZip(result.getZipBytes(),
        "profilesample/src/main/java/com/ovaledge/csp/apps/profilesample/main/ProfilesampleProfilingService.java");
    assertTrue(profilingService.contains("profileFile"));
    assertFalse(profilingService.contains("public long getRowCount"));
    assertFalse(profilingService.contains("profileColumn"));
    assertFalse(profilingService.contains("sampleProfile"));

    String profilingUnitTest = readTextFromZip(result.getZipBytes(),
        "profilesample/src/test/java/com/ovaledge/csp/tests/unit/connector/profilesample/ProfilesampleProfilingServiceUnitTest.java");
    assertTrue(profilingUnitTest.contains("profileFile_returnsEmptyResponseForStub"));
  }

  @Test
  void generateZip_profilingDbms_withFileKindOnRest_keepsDbmsMode(@TempDir Path tempDir) throws Exception {
    Path repoRoot = tempDir.resolve("repo");
    writeMinimalRepo(repoRoot);

    ConnectorGeneratorRequest request = buildProfilingRequest("DBMS", "REST",
        List.of(
            option("CRAWLER_PREFERENCE", "S"),
            option("CRAWLER_PREFERENCE", "C"),
            option("PROFILE_TYPES", "A"),
            option("PROFILE_TYPES", "S")),
        "profilefiledbms");
    request.setObjectKinds(List.of("FILE"));
    ConnectorGeneratorResult result = new ConnectorGeneratorService()
        .generate(request, pngIcon64x64());

    JsonNode manifest = readManifestFromZip(result.getZipBytes(),
        "profilefiledbms/src/main/resources/configs/profilefiledbms.json");
    assertTrue(manifest.path("connectorMaster").path("profiling").asBoolean());
    assertTrue(manifest.path("connectorMaster").path("sampleProfiling").asBoolean());
    assertTrue(hasCrawlerOption(manifest, "PROFILE_TYPES", "A"));
    assertTrue(hasCrawlerOption(manifest, "PROFILE_TYPES", "S"));

    String profilingService = readTextFromZip(result.getZipBytes(),
        "profilefiledbms/src/main/java/com/ovaledge/csp/apps/profilefiledbms/main/ProfilefiledbmsProfilingService.java");
    assertTrue(profilingService.contains("profileFile"));
  }

  @Test
  void generateZip_profilingDbms_withoutJdbcOrFile_fallsBackToSample(@TempDir Path tempDir) throws Exception {
    Path repoRoot = tempDir.resolve("repo");
    writeMinimalRepo(repoRoot);

    ConnectorGeneratorRequest request = buildProfilingRequest("DBMS", "REST",
        List.of(
            option("CRAWLER_PREFERENCE", "S"),
            option("CRAWLER_PREFERENCE", "C"),
            option("PROFILE_TYPES", "S")),
        "profilefallback");
    request.setObjectKinds(List.of("ENTITY"));
    ConnectorGeneratorResult result = new ConnectorGeneratorService()
        .generate(request, pngIcon64x64());

    String profilingService = readTextFromZip(result.getZipBytes(),
        "profilefallback/src/main/java/com/ovaledge/csp/apps/profilefallback/main/ProfilefallbackProfilingService.java");
    assertTrue(profilingService.contains("sampleProfile"));
    assertTrue(profilingService.contains("public long getRowCount"));
  }

  private ConnectorGeneratorRequest buildProfilingRequest(
      String profilingMode, String protocol, List<ConnectorGeneratorRequest.CrawlerOptionInput> options) {
    return buildProfilingRequest(profilingMode, protocol, options, null);
  }

  private ConnectorGeneratorRequest buildProfilingRequest(
      String profilingMode, String protocol, List<ConnectorGeneratorRequest.CrawlerOptionInput> options,
      String connectorNameOverride) {
    String name = connectorNameOverride != null ? connectorNameOverride
        : ("DBMS".equals(profilingMode) ? "profiledbms" : "profilesample");
    ConnectorGeneratorRequest request = buildRequest(List.of("ENTITY"), null);
    request.setConnectorName(name);
    request.setProfilingMode(profilingMode);
    request.getManifest().getConnectorMaster().setProtocol(protocol);
    request.getManifest().getConnectorMaster().setProfiling(true);
    request.getManifest().setCrawlerOptions(options);
    return request;
  }

  private static ConnectorGeneratorRequest.CrawlerOptionInput option(String type, String key) {
    ConnectorGeneratorRequest.CrawlerOptionInput o = new ConnectorGeneratorRequest.CrawlerOptionInput();
    o.setOptionType(type);
    o.setOptionKey(key);
    return o;
  }

  private static boolean hasCrawlerOption(JsonNode manifest, String optionType, String optionKey) {
    for (JsonNode n : manifest.path("crawlerOptions")) {
      if (optionType.equals(n.path("optionType").asText())
          && optionKey.equals(n.path("optionKey").asText())) {
        return true;
      }
    }
    return false;
  }

  private static boolean zipContains(byte[] zipBytes, String entryName) throws IOException {
    try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zipIn.getNextEntry()) != null) {
        if (entryName.equals(entry.getName())) {
          return true;
        }
      }
    }
    return false;
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
    assertTrue(assemblyPom.indexOf("<artifactId>wiretest</artifactId>") < assemblyPom.indexOf("<!-- Test dependencies"));
    assertTrue(parentPom.contains("<csp.sdk.test.report.modules>assembly,csp-api,wiretest</csp.sdk.test.report.modules>"));

    String modulePom = Files.readString(repoRoot.resolve("wiretest/pom.xml"), StandardCharsets.UTF_8);
    assertTrue(modulePom.contains("<version>9.9.9-TEST</version>"));
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
          <parent>
            <groupId>com.ovaledge</groupId>
            <artifactId>oe-dependencies</artifactId>
            <version>[8300.1.1,8300.100.100)</version>
          </parent>
          <artifactId>oe-csp-sdk</artifactId>
          <version>9.9.9-TEST</version>
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
        <!-- Test dependencies for git test -->
        </dependencies></project>
        """.formatted(DEP_MARKER), StandardCharsets.UTF_8);
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
