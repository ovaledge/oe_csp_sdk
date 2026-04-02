package com.ovaledge.csp.apps.awsconsole.client;

import com.ovaledge.csp.apps.awsconsole.constants.AwsConsoleConstants;
import com.ovaledge.csp.v3.core.connectionpool.core.ConnectionPoolManager;
import com.ovaledge.csp.v3.core.model.ClientConfig;
import com.ovaledge.csp.v3.core.model.ConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeRegionsRequest;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;
import software.amazon.awssdk.services.sts.model.StsException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Client for AWS API calls using AWS SDK for Java 2.x.
 * Uses ConnectionPoolManager with ResourceType.CLIENT to obtain a pooled AwsClientHolder
 * (STS + credentials + region). On auth failure calls ConnectionPoolManager.removeResource.
 */
public class AwsConsoleClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AwsConsoleClient.class);

    /**
     * Builds ClientConfig for the CLIENT pool from ConnectionConfig.
     */
    public static ClientConfig toClientConfig(ConnectionConfig config) {
        if (config == null) return null;
        String region = getRegion(config);
        return new ClientConfig(
                AwsConsoleConstants.SERVER_TYPE,
                config.getConnectionInfoId(),
                AwsConsoleConstants.SERVER_TYPE,
                getAccessKeyId(config),
                getSecretAccessKey(config),
                region);
    }

    /**
     * Validates credentials by calling STS GetCallerIdentity using the pooled client.
     * On auth failure (e.g. InvalidClientTokenId), evicts the connection from the pool.
     * On failure throws RuntimeException with the AWS error message so the caller can include it in the response.
     */
    public boolean validateConnection(ConnectionConfig config) {
        if (config == null) return false;
        ClientConfig clientConfig = toClientConfig(config);
        if (clientConfig == null) return false;
        try {
            Object resource = ConnectionPoolManager.getInstance().getClient(clientConfig);
            if (!(resource instanceof AwsClientHolder)) {
                LOGGER.warn("Pool did not return AwsClientHolder for awsconsole");
                throw new RuntimeException("Failed to obtain AWS client from pool.");
            }
            AwsClientHolder holder = (AwsClientHolder) resource;
            holder.getStsClient().getCallerIdentity(GetCallerIdentityRequest.builder().build());
            return true;
        } catch (StsException e) {
            if (isAuthFailure(e)) evictResource(config);
            LOGGER.warn("AWS STS validation failed: {}", e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : "AWS credentials validation failed.";
            throw new RuntimeException(msg, e);
        } catch (Exception e) {
            LOGGER.warn("AWS validation failed: {}", e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : "AWS credentials validation failed.";
            throw new RuntimeException(msg, e);
        }
    }

    /**
     * Returns list of AWS region names (containers) using the pooled client.
     * On failure (e.g. permission denied) throws RuntimeException with the AWS error message
     * so the caller can include it in the response.
     */
    public List<String> listRegions(ConnectionConfig config) {
        List<String> regions = new ArrayList<>();
        if (config == null) return regions;
        ClientConfig clientConfig = toClientConfig(config);
        if (clientConfig == null) return regions;
        try {
            Object resource = ConnectionPoolManager.getInstance().getClient(clientConfig);
            if (!(resource instanceof AwsClientHolder)) return regions;
            AwsClientHolder holder = (AwsClientHolder) resource;
            String defaultRegion = holder.getDefaultRegion();
            try (Ec2Client ec2 = holder.createEc2Client(defaultRegion)) {
                ec2.describeRegions(DescribeRegionsRequest.builder().build())
                        .regions()
                        .forEach(r -> regions.add(r.regionName()));
            }
        } catch (Exception e) {
            LOGGER.warn("AWS list regions failed: {}", e.getMessage());
            if (isAuthFailure(e)) evictResource(config);
            String msg = e.getMessage() != null ? e.getMessage() : "Failed to list AWS regions.";
            throw new RuntimeException(msg, e);
        }
        return regions;
    }

    /**
     * Returns EC2 instance IDs (and minimal info) for the given region using the pooled client.
     * Uses server-side pagination via maxResults and nextToken to avoid loading all instances into memory.
     * On failure (e.g. permission denied) throws RuntimeException with the AWS error message
     * so the caller can include it in the response.
     */
    public List<Map<String, Object>> listEc2Instances(ConnectionConfig config, String region, Integer limit, Integer offset) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (config == null || region == null || region.isBlank()) return rows;
        ClientConfig clientConfig = toClientConfig(config);
        if (clientConfig == null) return rows;
        int lim = limit != null && limit > 0 ? limit : 100;
        int off = offset != null && offset >= 0 ? offset : 0;
        try {
            Object resource = ConnectionPoolManager.getInstance().getClient(clientConfig);
            if (!(resource instanceof AwsClientHolder)) return rows;
            AwsClientHolder holder = (AwsClientHolder) resource;
            try (Ec2Client ec2 = holder.createEc2Client(region)) {
                // Use server-side pagination with maxResults and nextToken
                int totalFetched = 0;
                int toSkip = off;
                String nextToken = null;
                // Fetch in batches; each batch requests up to (remaining needed + toSkip) capped at 1000
                do {
                    int batchSize = Math.max(5, Math.min(1000, lim + toSkip - rows.size()));
                    DescribeInstancesRequest.Builder reqBuilder = DescribeInstancesRequest.builder()
                            .maxResults(batchSize);
                    if (nextToken != null) {
                        reqBuilder.nextToken(nextToken);
                    }
                    DescribeInstancesResponse resp = ec2.describeInstances(reqBuilder.build());
                    for (Reservation res : resp.reservations()) {
                        for (software.amazon.awssdk.services.ec2.model.Instance inst : res.instances()) {
                            totalFetched++;
                            if (toSkip > 0) {
                                toSkip--;
                                continue;
                            }
                            if (rows.size() < lim) {
                                rows.add(Map.<String, Object>of(
                                        "instanceId", inst.instanceId() != null ? inst.instanceId() : "",
                                        "instanceType", inst.instanceType() != null ? inst.instanceType().toString() : "",
                                        "state", inst.state() != null && inst.state().name() != null ? inst.state().name().toString() : "",
                                        "region", region
                                ));
                            }
                        }
                    }
                    nextToken = resp.nextToken();
                } while (nextToken != null && rows.size() < lim);
            }
        } catch (Ec2Exception e) {
            if (isAuthFailure(e)) evictResource(config);
            LOGGER.warn("AWS list EC2 instances failed: {}", e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : "Failed to list EC2 instances.";
            throw new RuntimeException(msg, e);
        } catch (Exception e) {
            LOGGER.warn("AWS list EC2 instances failed: {}", e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : "Failed to list EC2 instances.";
            throw new RuntimeException(msg, e);
        }
        return rows;
    }

    /**
     * Lists S3 buckets for the given credentials. S3 buckets are global (not per-region),
     * but the client requires a signing region. Uses the default region from the connection config.
     * <p>
     * The S3 ListBuckets API returns all buckets in a single response (no server-side pagination),
     * so offset/limit are applied in memory.
     *
     * @param config connection config with AWS credentials
     * @param limit  max number of buckets to return
     * @param offset number of buckets to skip
     * @return list of bucket maps with keys: bucketName, creationDate
     */
    public List<Map<String, Object>> listS3Buckets(ConnectionConfig config, int limit, int offset) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (config == null) return rows;
        ClientConfig clientConfig = toClientConfig(config);
        if (clientConfig == null) return rows;
        int lim = limit > 0 ? limit : 100;
        int off = Math.max(offset, 0);
        try {
            Object resource = ConnectionPoolManager.getInstance().getClient(clientConfig);
            if (!(resource instanceof AwsClientHolder)) return rows;
            AwsClientHolder holder = (AwsClientHolder) resource;
            try (S3Client s3 = holder.createS3Client(null)) {
                ListBucketsResponse resp = s3.listBuckets(ListBucketsRequest.builder().build());
                List<Bucket> buckets = resp.buckets();
                int end = Math.min(off + lim, buckets.size());
                for (int i = off; i < end; i++) {
                    Bucket b = buckets.get(i);
                    rows.add(Map.<String, Object>of(
                            "bucketName", b.name() != null ? b.name() : "",
                            "creationDate", b.creationDate() != null ? b.creationDate().toString() : ""
                    ));
                }
            }
        } catch (S3Exception e) {
            if (isAuthFailure(e)) evictResource(config);
            LOGGER.warn("AWS list S3 buckets failed: {}", e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : "Failed to list S3 buckets.";
            throw new RuntimeException(msg, e);
        } catch (Exception e) {
            LOGGER.warn("AWS list S3 buckets failed: {}", e.getMessage());
            String msg = e.getMessage() != null ? e.getMessage() : "Failed to list S3 buckets.";
            throw new RuntimeException(msg, e);
        }
        return rows;
    }

    public static String getAccessKeyId(ConnectionConfig config) {
        if (config == null) return null;
        if (config.getUsername() != null && !config.getUsername().isBlank()) return config.getUsername();
        return getFromAdditionalAttributes(config, AwsConsoleConstants.KEY_ACCESS_KEY_ID);
    }

    public static String getSecretAccessKey(ConnectionConfig config) {
        if (config == null) return null;
        if (config.getPassword() != null && !config.getPassword().isBlank()) return config.getPassword();
        return getFromAdditionalAttributes(config, AwsConsoleConstants.KEY_SECRET_ACCESS_KEY);
    }

    public static String getRegion(ConnectionConfig config) {
        if (config == null) return AwsConsoleConstants.DEFAULT_REGION;
        String r = getFromAdditionalAttributes(config, AwsConsoleConstants.KEY_REGION);
        return (r != null && !r.isBlank()) ? r.trim() : AwsConsoleConstants.DEFAULT_REGION;
    }

    private static String getFromAdditionalAttributes(ConnectionConfig config, String key) {
        Map<String, String> attrs = config.getAdditionalAttributes();
        if (attrs == null) return null;
        String v = attrs.get(key);
        return (v != null && !v.isBlank()) ? v.trim() : null;
    }

    private static boolean isAuthFailure(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("InvalidClientTokenId") || msg.contains("SignatureDoesNotMatch")
                || msg.contains("InvalidAccessKeyId") || msg.contains("UnrecognizedClientException");
    }

    private static void evictResource(ConnectionConfig config) {
        Integer id = config.getConnectionInfoId();
        if (id != null) {
            ConnectionPoolManager.getInstance().removeResource(id);
        }
    }
}
