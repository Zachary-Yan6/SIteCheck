package com.zachary.sitecheck.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/** Which host ends up in the clients and presigned URLs for LocalStack, a phone on Wi-Fi, and real AWS. */
class S3ConfigTests {

    private final S3Config config = new S3Config();

    @Test
    void localStackUsesPathStyleUrlsOnTheEndpoint() {
        S3Properties local = properties("http://localhost:4566", "");
        try (S3Client client = config.s3Client(local); S3Presigner presigner = config.s3Presigner(local)) {
            assertThat(client).isNotNull();
            assertThat(presignedUrl(presigner)).startsWith("http://localhost:4566/sitecheck-photos/photos/x.jpg");
        }
    }

    @Test
    void presignEndpointWinsSoThePhoneCanReachIt() {
        S3Properties lan = properties("http://localhost:4566", "http://192.168.1.20:4566");
        try (S3Presigner presigner = config.s3Presigner(lan)) {
            assertThat(presignedUrl(presigner)).startsWith("http://192.168.1.20:4566/sitecheck-photos/");
        }
    }

    @Test
    void realAwsUsesTheRegionalVirtualHostedUrl() {
        // The default credential chain is only consulted when signing; give it something to find.
        System.setProperty("aws.accessKeyId", "unit-test");
        System.setProperty("aws.secretAccessKey", "unit-test");
        try (S3Client client = config.s3Client(properties("", "")); S3Presigner presigner = config.s3Presigner(properties("", ""))) {
            assertThat(client).isNotNull();
            assertThat(presignedUrl(presigner)).startsWith("https://sitecheck-photos.s3.eu-west-1.amazonaws.com/");
        } finally {
            System.clearProperty("aws.accessKeyId");
            System.clearProperty("aws.secretAccessKey");
        }
    }

    private static String presignedUrl(S3Presigner presigner) {
        PresignedPutObjectRequest request = presigner.presignPutObject(r -> r
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(put -> put.bucket("sitecheck-photos").key("photos/x.jpg")));
        return request.url().toString();
    }

    private static S3Properties properties(String endpoint, String presignEndpoint) {
        return new S3Properties("sitecheck-photos", "eu-west-1", endpoint, presignEndpoint, Duration.ofMinutes(10), 1024);
    }
}
