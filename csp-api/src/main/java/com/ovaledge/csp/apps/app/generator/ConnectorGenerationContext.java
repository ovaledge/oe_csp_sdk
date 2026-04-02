package com.ovaledge.csp.apps.app.generator;

import com.ovaledge.csp.v3.core.apps.model.ObjectKind;

import java.util.ArrayList;
import java.util.List;

public class ConnectorGenerationContext {

    private final String connectorName;
    private final String artifactId;
    private final String packageName;
    private final String classPrefix;
    private final String serverType;
    private final List<ObjectKind> objectKinds;

    public ConnectorGenerationContext(String connectorName, String artifactId, String packageName,
            String classPrefix, String serverType, List<ObjectKind> objectKinds) {
        this.connectorName = connectorName;
        this.artifactId = artifactId;
        this.packageName = packageName;
        this.classPrefix = classPrefix;
        this.serverType = serverType;
        this.objectKinds = objectKinds != null ? new ArrayList<>(objectKinds) : new ArrayList<>();
    }

    public String getConnectorName() {
        return connectorName;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassPrefix() {
        return classPrefix;
    }

    public String getServerType() {
        return serverType;
    }

    public List<ObjectKind> getObjectKinds() {
        return new ArrayList<>(objectKinds);
    }
}
