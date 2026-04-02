package com.ovaledge.csp.apps.monetdb.quick;

import com.ovaledge.csp.apps.monetdb.constants.MonetDBConstants;
import com.ovaledge.csp.apps.monetdb.main.MonetDBConnector;
import com.ovaledge.csp.v3.core.apps.model.response.SupportedObjectsResponse;
import com.ovaledge.csp.v3.core.apps.model.response.ValidateConnectionResponse;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for standalone testing of the MonetDB connector.
 * Base path: /api/monetdb
 */
@RestController
@RequestMapping("/api/" + MonetDBConstants.SERVER_TYPE)
@CrossOrigin(origins = "${csp.cors.allowed-origins:http://localhost:3000}")
public class MonetDBController {

    private final MonetDBConnector connector = new MonetDBConnector();

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
        if (config == null) {
            return ResponseEntity.badRequest().body(
                    new ValidateConnectionResponse().withSuccess(false).withValid(false).withMessage("ConnectionConfig is required"));
        }
        try {
            ValidateConnectionResponse response = connector.validateConnection(config);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.ok(
                    new ValidateConnectionResponse().withSuccess(false).withValid(false).withMessage("Validation failed: " + e.getMessage()));
        }
    }
}
