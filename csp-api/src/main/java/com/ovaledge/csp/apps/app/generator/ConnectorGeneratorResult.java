package com.ovaledge.csp.apps.app.generator;

public class ConnectorGeneratorResult {

    private final String filename;
    private final byte[] zipBytes;

    public ConnectorGeneratorResult(String filename, byte[] zipBytes) {
        this.filename = filename;
        this.zipBytes = zipBytes;
    }

    public String getFilename() {
        return filename;
    }

    public byte[] getZipBytes() {
        return zipBytes;
    }
}
