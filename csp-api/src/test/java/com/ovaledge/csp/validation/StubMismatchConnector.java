package com.ovaledge.csp.validation;

/**
 * Stand-in connector for config/runtime mismatch tests: runtime reports {@code monetdb} while tests
 * create {@code configs/wrongname.json}.
 */
public class StubMismatchConnector {

    public StubMismatchConnector() {}

    /** Intentionally differs from the config JSON basename in {@link SdkConnectorReactorScannerTest}. */
    public String getServerType() {
        return "monetdb";
    }
}
