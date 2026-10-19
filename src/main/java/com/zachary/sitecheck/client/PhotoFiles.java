package com.zachary.sitecheck.client;

import com.zachary.sitecheck.config.S3Properties;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** Reads photo bytes from S3 on the server side, e.g. to embed them in the PDF report. */
@Component
public class PhotoFiles {

    private final S3Client s3Client;
    private final S3Properties properties;

    public PhotoFiles(S3Client s3Client, S3Properties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /** The photo's JPEG bytes, or empty if S3 doesn't have it. */
    public Optional<byte[]> read(String s3Key) {
        try {
            return Optional.of(s3Client.getObjectAsBytes(r -> r.bucket(properties.bucket()).key(s3Key)).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }
}
