package com.ovaledge.csp.tests.unit.connector.cspapi;

/** Stand-in for Apps {@code quickbooks} module where folder name differs from {@code getServerType()}. */
public class StubQuickbooksOnlineConnector {

    public StubQuickbooksOnlineConnector() {}

    public String getServerType() {
        return "quickbooks-online";
    }
}
