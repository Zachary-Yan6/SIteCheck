package com.zachary.sitecheck.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 client (server-side calls, e.g. checking an upload exists) and presigner (URLs the phone
 * uploads to directly, so photo bytes never pass through this server).
 *
 * <p>With {@code sitecheck.s3.endpoint} set we talk to LocalStack: dummy credentials and
 * path-style URLs (http://host:4566/bucket/key). Without it we use real AWS, with credentials
 * from the default chain - on EC2 that is the instance role, so no keys live in config.
 */
@Configuration
public class S3Config {

    @Bean
    S3Client s3Client(S3Properties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties));
        if (StringUtils.hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(S3Properties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties));
        String endpoint = StringUtils.hasText(properties.presignEndpoint())
                ? properties.presignEndpoint()
                : properties.endpoint();
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    private static AwsCredentialsProvider credentials(S3Properties properties) {
        return StringUtils.hasText(properties.endpoint())
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))
                : DefaultCredentialsProvider.builder().build();
    }
}
