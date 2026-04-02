package com.ovaledge.csp.apps.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * REST Controller for streaming application logs via Server-Sent Events (SSE).
 * <p>
 * Provides real-time log streaming capabilities to connected clients, allowing
 * them to monitor application logs as they occur.
 * </p>
 */
@RestController
@RequestMapping("/v1/logs")
@CrossOrigin(origins = "${csp.cors.allowed-origins:http://localhost:3000}")
public class LogsStreamController {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Thread-safe list to hold all active SSE emitters
    private static final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    
    // Heartbeat executor to keep connections alive (daemon thread so it doesn't block JVM shutdown)
    private static final ScheduledExecutorService heartbeatExecutor = 
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SSE-Heartbeat");
            t.setDaemon(true);
            return t;
        });
    
    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
        for (SseEmitter emitter : emitters) {
            try { emitter.complete(); } catch (Exception ignored) { }
        }
        emitters.clear();
    }
    
    static {
        // Send heartbeat every 30 seconds to keep connections alive
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            Map<String, Object> heartbeat = new HashMap<>();
            heartbeat.put("type", "heartbeat");
            heartbeat.put("timestamp", Instant.now().toString());
            broadcastToAll(heartbeat);
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Endpoint to establish SSE connection for log streaming.
     * 
     * @return SseEmitter for streaming logs
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs() {

        // Create SSE emitter with 1 hour timeout
        SseEmitter emitter = new SseEmitter(3600000L);
        
        // Add emitter to the list
        emitters.add(emitter);
        
        // Remove emitter on completion or timeout
        emitter.onCompletion(() -> {
            emitters.remove(emitter);
        });
        
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
        });
        
        emitter.onError((ex) -> {
            emitters.remove(emitter);
            // Only log non-broken-pipe errors
            if (!(ex.getMessage() != null && ex.getMessage().contains("Broken pipe"))) {
                System.err.println("Error in logs stream: " + ex.getMessage());
            }
        });
        
        // Send initial connection message
        try {
            Map<String, Object> connectionMsg = new HashMap<>();
            connectionMsg.put("message", "Connected to CSP-API logs stream");
            connectionMsg.put("level", "INFO");
            connectionMsg.put("timestamp", Instant.now().toString());
            
            emitter.send(SseEmitter.event()
                .data(objectMapper.writeValueAsString(connectionMsg)));
        } catch (IOException e) {
            System.err.println("Error sending initial message: " + e.getMessage());
            emitter.completeWithError(e);
        }
        
        return emitter;
    }
    
    /**
     * Broadcasts a log entry to all connected clients.
     * 
     * @param logEntry the log entry to broadcast
     */
    public static void broadcastLog(Map<String, Object> logEntry) {
        broadcastToAll(logEntry);
    }
    
    /**
     * Broadcasts a message to all connected clients.
     * 
     * @param data the data to broadcast
     */
    private static void broadcastToAll(Map<String, Object> data) {
        // Create a copy to avoid concurrent modification
        CopyOnWriteArrayList<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .data(objectMapper.writeValueAsString(data)));
            } catch (IOException e) {
                // Mark emitter as dead
                deadEmitters.add(emitter);
            }
        }
        
        // Remove dead emitters
        emitters.removeAll(deadEmitters);
    }
    
    /**
     * Get the count of active connections.
     * 
     * @return number of active SSE connections
     */
    public static int getActiveConnectionsCount() {
        return emitters.size();
    }
}
