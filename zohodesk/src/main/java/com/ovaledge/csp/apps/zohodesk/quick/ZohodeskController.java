package com.ovaledge.csp.apps.zohodesk.quick;

import com.ovaledge.csp.apps.zohodesk.main.ZohodeskConnector;
import com.ovaledge.csp.v3.core.apps.model.response.ValidateConnectionResponse;
import com.ovaledge.csp.v3.core.apps.model.response.SupportedObjectsResponse;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/zohodesk")
public class ZohodeskController {

    private final ZohodeskConnector connector = new ZohodeskConnector();

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

    @PostMapping("/connection/validate")
    public ResponseEntity<ValidateConnectionResponse> validateConnection(@RequestBody ConnectionConfig config) {
        return ResponseEntity.ok(connector.validateConnection(config));
    }
}
