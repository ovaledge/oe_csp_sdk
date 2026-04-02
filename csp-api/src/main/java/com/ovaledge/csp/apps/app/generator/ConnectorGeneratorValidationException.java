package com.ovaledge.csp.apps.app.generator;

import java.util.ArrayList;
import java.util.List;

public class ConnectorGeneratorValidationException extends RuntimeException {

    private final List<String> errors;

    public ConnectorGeneratorValidationException(List<String> errors) {
        super("Validation failed");
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
}
