package com.ovaledge.csp.apps.app.service;

import com.ovaledge.csp.v3.core.apps.SdkVersion;
import com.ovaledge.csp.v3.core.apps.service.AppsConnector;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Registry service that discovers and manages all available AppsConnector implementations
 * via Java's ServiceLoader mechanism.
 */
@Service
public class AppsRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(AppsRegistry.class);
    
    private final Map<String, AppsConnector> connectorsByType = new ConcurrentHashMap<>();
    private final List<AppsConnector> allConnectors = new CopyOnWriteArrayList<>();
    
    @PostConstruct
    public void initialize() {
        logger.info("Initializing connector registry...");
        
        ServiceLoader<AppsConnector> serviceLoader = ServiceLoader.load(AppsConnector.class);
        
        for (AppsConnector connector : serviceLoader) {
            try {
                String serverType = connector.getServerType();
                if (serverType != null && !serverType.isEmpty()) {
                    connectorsByType.put(serverType.toLowerCase(), connector);
                    allConnectors.add(connector);
                    logger.info("Registered connector: {} (serverType: {})",
                        connector.getClass().getName(), serverType);
                    String connectorSdkVersion = connector.getSdkVersion();
                    if (connectorSdkVersion != null && !SdkVersion.SDK_VERSION.equals(connectorSdkVersion)) {
                        logger.warn("Connector '{}' (type: {}) was built against SDK version {} "
                                + "but the running SDK version is {}. This may cause compatibility issues.",
                                connector.getClass().getName(), serverType, connectorSdkVersion, SdkVersion.SDK_VERSION);
                    }
                } else {
                    logger.warn("Skipping connector {} - no serverType defined", 
                        connector.getClass().getName());
                }
            } catch (Exception e) {
                logger.error("Error initializing connector: {}", connector.getClass().getName(), e);
            }
        }
        
        logger.info("Connector registry initialized. Found {} connector(s): {}", 
            allConnectors.size(), 
            connectorsByType.keySet().stream().collect(Collectors.joining(", ")));
    }
    
    /**
     * Gets a connector by server type (case-insensitive).
     * @param serverType the server type (e.g., "monetdb")
     * @return the connector instance, or null if not found
     */
    public AppsConnector getConnector(String serverType) {
        if (serverType == null || serverType.isEmpty()) {
            return null;
        }
        return connectorsByType.get(serverType.toLowerCase());
    }
    
    /**
     * Gets all registered connectors.
     * @return list of all connectors
     */
    public List<AppsConnector> getAllConnectors() {
        return new ArrayList<>(allConnectors);
    }
    
    /**
     * Gets all registered server types.
     * @return set of server type strings
     */
    public Set<String> getAvailableServerTypes() {
        return new HashSet<>(connectorsByType.keySet());
    }
    
    /**
     * Checks if a connector is available for the given server type.
     * @param serverType the server type to check
     * @return true if connector is available, false otherwise
     */
    public boolean hasConnector(String serverType) {
        return serverType != null && connectorsByType.containsKey(serverType.toLowerCase());
    }
}
