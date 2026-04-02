package com.ovaledge.csp.apps.app.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Web controller for serving the single-page application.
 */
@RestController
public class WebController {
    
    /**
     * Serves the main single-page application.
     *
     * @return the HTML content
     */
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> index() {
        try {
            Resource resource = new ClassPathResource("static/index.html");
            try (InputStream is = resource.getInputStream()) {
                String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return ResponseEntity.ok().body(html);
            }
        } catch (IOException e) {
            String safeMessage = e.getMessage() != null
                    ? e.getMessage().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                    : "Unknown error";
            return ResponseEntity.internalServerError()
                .body("<html><body><h1>Error loading application</h1><p>" + safeMessage + "</p></body></html>");
        }
    }
}
