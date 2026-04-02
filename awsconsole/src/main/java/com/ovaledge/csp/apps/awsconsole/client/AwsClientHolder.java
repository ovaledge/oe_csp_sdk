package com.ovaledge.csp.apps.awsconsole.client;

import com.ovaledge.csp.apps.awsconsole.constants.AwsConsoleConstants;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sts.StsClient;

import java.io.Closeable;
import java.io.IOException;

/**
 * Holder for pooled AWS SDK clients used by the awsconsole connector.
 * Wraps StsClient and credentials/region so the connector can validate (STS)
 * and create region-specific service clients (e.g. EC2) without pooling each one.
 */
public class AwsClientHolder implements Closeable {

    private final StsClient stsClient;
    private final String defaultRegion;
    private final AwsCredentialsProvider credentialsProvider;

    public AwsClientHolder(StsClient stsClient, String defaultRegion, AwsCredentialsProvider credentialsProvider) {
        this.stsClient = stsClient;
        this.defaultRegion = (defaultRegion != null && !defaultRegion.isBlank()) ? defaultRegion : AwsConsoleConstants.DEFAULT_REGION;
        this.credentialsProvider = credentialsProvider;
    }

    public StsClient getStsClient() {
        return stsClient;
    }

    public String getDefaultRegion() {
        return defaultRegion;
    }

    public AwsCredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    /**
     * Creates a new EC2 client for the given region. Caller must close it when done.
     */
    public Ec2Client createEc2Client(String region) {
        String r = (region != null && !region.isBlank()) ? region : defaultRegion;
        return Ec2Client.builder()
                .region(Region.of(r))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    /**
     * Creates a new S3 client for the given region. Caller must close it when done.
     * S3 buckets are global but the client still needs a region for signing requests.
     */
    public S3Client createS3Client(String region) {
        String r = (region != null && !region.isBlank()) ? region : defaultRegion;
        return S3Client.builder()
                .region(Region.of(r))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Override
    public void close() throws IOException {
        if (stsClient != null) {
            stsClient.close();
        }
    }
}
