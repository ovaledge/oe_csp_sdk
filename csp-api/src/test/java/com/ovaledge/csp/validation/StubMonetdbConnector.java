package com.ovaledge.csp.validation;

/**
 * Minimal stand-in for an {@code AppsConnector} implementation used in {@link SdkConnectorReactorScannerTest}.
 *
 * <p>Listed in a synthetic {@code META-INF/services} file so the scanner can reflectively call
 * {@link #getServerType()} without pulling in real connector dependencies.
 */
public class StubMonetdbConnector {

    public StubMonetdbConnector() {}

    /** Matches the {@code configs/monetdb.json} basename written by the test harness. */
    public String getServerType() {
        return "monetdb";
    }
}
