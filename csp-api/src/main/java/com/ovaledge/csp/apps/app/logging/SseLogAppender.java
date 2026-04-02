package com.ovaledge.csp.apps.app.logging;

import com.ovaledge.csp.apps.app.controller.LogsStreamController;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom Log4j appender that broadcasts log events to SSE clients.
 * <p>
 * This appender captures log events and sends them to all connected
 * SSE clients via the LogsStreamController.
 * </p>
 */
public class SseLogAppender extends AppenderSkeleton {
    
    public SseLogAppender() {
        super();
    }
    
    @Override
    public void activateOptions() {
        super.activateOptions();
    }
    
    @Override
    protected void append(LoggingEvent event) {
        try {
            // Prevent circular logging - don't broadcast logs from the LogsStreamController itself
            String loggerName = event.getLoggerName();
            if (loggerName != null && loggerName.contains("LogsStreamController")) {
                return;
            }
            
            // Create log entry map
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
            logEntry.put("level", event.getLevel().toString());
            logEntry.put("logger", event.getLoggerName());
            logEntry.put("message", event.getRenderedMessage());
            logEntry.put("thread", event.getThreadName());
            
            // Add location info
            if (event.getLocationInformation() != null) {
                logEntry.put("fileName", event.getLocationInformation().getFileName());
                logEntry.put("lineNumber", event.getLocationInformation().getLineNumber());
            }
            
            // Add exception info if present
            if (event.getThrowableInformation() != null) {
                Throwable throwable = event.getThrowableInformation().getThrowable();
                if (throwable != null) {
                    logEntry.put("exception", throwable.getClass().getName());
                    logEntry.put("exceptionMessage", throwable.getMessage());
                }
            }
            
            // Broadcast to all connected clients
            int activeConnections = LogsStreamController.getActiveConnectionsCount();
            if (activeConnections > 0) {
                LogsStreamController.broadcastLog(logEntry);
            }
        } catch (Exception e) {
            // Don't let logging errors break the application
            // Also don't use errorHandler as it might cause more logging
            System.err.println("Error broadcasting log event: " + e.getMessage());
        }
    }
    
    @Override
    public void close() {
        // Cleanup if needed
    }
    
    @Override
    public boolean requiresLayout() {
        return false;
    }
}
