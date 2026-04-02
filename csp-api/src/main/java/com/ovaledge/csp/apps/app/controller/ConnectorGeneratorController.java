package com.ovaledge.csp.apps.app.controller;

import com.ovaledge.csp.apps.app.generator.ConnectorGeneratorRequest;
import com.ovaledge.csp.apps.app.generator.ConnectorGeneratorResult;
import com.ovaledge.csp.apps.app.generator.ConnectorGeneratorService;
import com.ovaledge.csp.apps.app.generator.ConnectorGeneratorValidationException;
import com.ovaledge.csp.v3.core.apps.model.ObjectKind;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/generator")
@CrossOrigin(origins = "${csp.cors.allowed-origins:http://localhost:3000}")
public class ConnectorGeneratorController {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorGeneratorController.class);

    private final ConnectorGeneratorService generatorService;

    public ConnectorGeneratorController(ConnectorGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    /**
     * Returns object-level kinds only (for the Create Connector UI).
     * Container-level kind (CONTAINER) is excluded — top-level in AppsConnectors hierarchy;
     * only object-level kinds (tables, views, files, reports, etc.) are selectable.
     *
     * @return list of { value, displayName, category, tooltip } for each object-level ObjectKind
     */
    @GetMapping("/object-kinds")
    public ResponseEntity<List<Map<String, String>>> getObjectKinds() {
        List<Map<String, String>> kinds = Arrays.stream(ObjectKind.values())
                .filter(kind -> kind != ObjectKind.CONTAINER && kind != ObjectKind.DASHBOARD)
                .map(kind -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("value", kind.value());
                    m.put("displayName", kind.getDisplayName());
                    m.put("category", kind.getCategory());
                    m.put("tooltip", kind.getTooltip());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(kinds);
    }

    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> generateConnector(
            @RequestParam("connectorName") String connectorName,
            @RequestParam("objectKinds") List<String> objectKinds,
            @RequestParam(value = "icon", required = false) MultipartFile icon) {

        try {
            ConnectorGeneratorRequest request = new ConnectorGeneratorRequest(connectorName, objectKinds);
            ConnectorGeneratorResult result = generatorService.generate(request, icon);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", result.getFilename());
            headers.setContentLength(result.getZipBytes().length);

            return new ResponseEntity<>(result.getZipBytes(), headers, HttpStatus.OK);
        } catch (ConnectorGeneratorValidationException ex) {
            return ResponseEntity.badRequest().body(errorResponse("Validation failed", ex.getErrors()));
        } catch (Exception ex) {
            logger.error("Connector generation failed", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("Connector generation failed", List.of(ex.getMessage())));
        }
    }

    private Map<String, Object> errorResponse(String message, List<String> errors) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", message);
        response.put("errors", errors);
        return response;
    }
}
