package com.ovaledge.csp.apps.app.generator;

import java.util.ArrayList;
import java.util.List;

public class ConnectorGeneratorRequest {

    private final String connectorName;
    private final List<String> objectKinds;

    public ConnectorGeneratorRequest(String connectorName, List<String> objectKinds) {
        this.connectorName = connectorName;
        this.objectKinds = objectKinds != null ? new ArrayList<>(objectKinds) : new ArrayList<>();
    }

    public String getConnectorName() {
        return connectorName;
    }

    public List<String> getObjectKinds() {
        return new ArrayList<>(objectKinds);
    }
}
