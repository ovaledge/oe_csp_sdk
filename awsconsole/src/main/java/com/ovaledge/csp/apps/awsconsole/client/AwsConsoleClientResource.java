package com.ovaledge.csp.apps.awsconsole.client;

import com.ovaledge.csp.v3.core.connectionpool.client.ClientResourceProvider;
import com.ovaledge.csp.v3.core.connectionpool.client.ClientResourceWrapper;
import com.ovaledge.csp.v3.core.model.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * Client resource wrapper for AWS Console connections.
 *
 * <p>Extends {@link ClientResourceWrapper} to handle AWS-specific client creation.
 * Creates an {@link AwsClientHolder} containing an STS client, credentials provider,
 * and default region. The holder can then be used to create region-specific service
 * clients (e.g. EC2) on demand.</p>
 *
 * <p>This class lives in the {@code awsconsole} connector module (not in
 * {@code csp-sdk-core}) and is discovered at runtime via Java {@link java.util.ServiceLoader SPI}.
 * This pattern allows any connector module — including third-party ones — to
 * provide their own {@link ClientResourceProvider} implementation without
 * modifying the core SDK.</p>
 *
 * <p><b>Required configuration:</b></p>
 * <ul>
 *   <li>{@code apiKey} — AWS access key ID</li>
 *   <li>{@code apiSecret} — AWS secret access key</li>
 *   <li>{@code region} (optional) — defaults to {@code us-east-1}</li>
 * </ul>
 *
 * @author OvalEdge Development Team
 * @see ClientResourceWrapper
 * @see ClientResourceProvider
 * @see AwsClientHolder
 */
public class AwsConsoleClientResource extends ClientResourceWrapper {

    private static final Logger logger = LoggerFactory.getLogger(AwsConsoleClientResource.class);
    private static final String DEFAULT_REGION = "us-east-1";

    /**
     * Creates a new AWS Console client resource from the given configuration.
     *
     * @param config the client configuration containing AWS credentials and region
     * @throws IllegalArgumentException if AWS credentials (apiKey/apiSecret) are missing or blank
     */
    public AwsConsoleClientResource(ClientConfig config) {
        super(createAwsClientHolder(config), config.getConnectionId(), config.getServerType(), config.getClientType());
    }

    /**
     * SPI provider — discovered by {@link java.util.ServiceLoader}.
     *
     * <p>Registered via
     * {@code META-INF/services/com.ovaledge.csp.v3.core.connectionpool.client.ClientResourceProvider}
     * in this module's resources. The {@link com.ovaledge.csp.v3.core.connectionpool.client.ClientResourceFactory}
     * loads all providers at startup and routes requests by {@code typeKey}.</p>
     */
    public static class Provider implements ClientResourceProvider {
        @Override
        public String typeKey() {
            return "awsconsole";
        }

        @Override
        public ClientResourceWrapper create(ClientConfig config) {
            return new AwsConsoleClientResource(config);
        }
    }

    private static AwsClientHolder createAwsClientHolder(ClientConfig config) {
        String accessKey = config.getApiKey();
        String secretKey = config.getApiSecret();
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("AWS credentials (apiKey/apiSecret) required for awsconsole client");
        }
        String region = (config.getRegion() != null && !config.getRegion().isBlank())
                ? config.getRegion().trim() : DEFAULT_REGION;
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);
        StsClient stsClient = StsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
        logger.debug("Created AWS client holder for connection {} region {}", config.getConnectionId(), region);
        return new AwsClientHolder(stsClient, region, credentialsProvider);
    }
}
