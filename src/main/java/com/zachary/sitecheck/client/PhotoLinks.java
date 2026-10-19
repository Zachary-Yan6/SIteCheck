package com.zachary.sitecheck.client;

import com.zachary.sitecheck.config.S3Properties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Short-lived read links for photos in the private bucket (NFR-05): the bucket is never public,
 * so anyone viewing a photo gets a presigned GET URL that expires with {@code presign-ttl}.
 */
@Component
public class PhotoLinks {

    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public PhotoLinks(S3Presigner s3Presigner, S3Properties properties) {
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    public String downloadUrl(String s3Key) {
        return s3Presigner.presignGetObject(r -> r
                        .signatureDuration(properties.presignTtl())
                        .getObjectRequest(get -> get.bucket(properties.bucket()).key(s3Key)))
                .url()
                .toString();
    }
}
