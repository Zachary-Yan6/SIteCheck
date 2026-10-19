package com.zachary.sitecheck.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 settings ({@code sitecheck.s3.*}).
 *
 * @param bucket          private bucket holding the photos (NFR-05)
 * @param region          AWS region, eu-west-1 (Ireland)
 * @param endpoint        set only for LocalStack, e.g. http://localhost:4566; blank = real AWS
 * @param presignEndpoint host put into presigned URLs; must be reachable from the phone, so for a
 *                        real device on Wi-Fi use the PC's LAN IP. Blank = same as {@code endpoint}
 * @param presignTtl      how long an upload URL stays valid (NFR-05: 5-10 minutes)
 * @param maxPhotoBytes   upload size limit; the app compresses photos to under 500KB (FR-13)
 */
@ConfigurationProperties("sitecheck.s3")
public record S3Properties(String bucket, String region, String endpoint, String presignEndpoint,
                           Duration presignTtl, long maxPhotoBytes) {
}
