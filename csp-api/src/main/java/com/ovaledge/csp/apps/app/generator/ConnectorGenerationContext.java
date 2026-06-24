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
    private final String primaryObject;

    public ConnectorGenerationContext(String connectorName, String artifactId, String packageName,
            String classPrefix, String serverType, List<ObjectKind> objectKinds, String primaryObject) {
        this.connectorName = connectorName;
        this.artifactId = artifactId;
        this.packageName = packageName;
        this.classPrefix = classPrefix;
        this.serverType = serverType;
        this.objectKinds = objectKinds != null ? new ArrayList<>(objectKinds) : new ArrayList<>();
        this.primaryObject = primaryObject;
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

    public String getPrimaryObject() {
        return primaryObject;
    }

    /**
     * Fully qualified class name of the generated connector.
     * Must match archetype templates (SPI, configs JSON) and {@code dtoRegisterName} in the manifest.
     */
    public String getConnectorFqcn() {
        return "com.ovaledge.csp.apps." + packageName + ".main." + classPrefix + "Connector";
    }
}
