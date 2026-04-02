package com.ovaledge.csp.apps.${artifactId}.quick;

import com.ovaledge.csp.apps.${artifactId}.main.${classPrefix}Connector;
import com.ovaledge.csp.v3.core.apps.model.response.SupportedObjectsResponse;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/${artifactId}")
public class ${classPrefix}Controller {

    private final ${classPrefix}Connector connector = new ${classPrefix}Connector();

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("connectorType", connector.getServerType());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/supported-objects")
    public ResponseEntity<SupportedObjectsResponse> supportedObjects() {
        return ResponseEntity.ok(connector.getMetadataService().getSupportedObjects());
    }
}
