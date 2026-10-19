package com.zachary.sitecheck.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zachary.sitecheck.config.S3Properties;
import com.zachary.sitecheck.dto.PhotoStatusResponse;
import com.zachary.sitecheck.dto.UploadUrlResponse;
import com.zachary.sitecheck.entity.Inspection;
import com.zachary.sitecheck.entity.Photo;
import com.zachary.sitecheck.entity.PhotoStatus;
import com.zachary.sitecheck.exception.ApiException;
import com.zachary.sitecheck.mapper.InspectionMapper;
import com.zachary.sitecheck.mapper.PhotoMapper;
import com.zachary.sitecheck.security.AuthUser;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * Photo upload in three steps (FR-22), so the photo bytes go straight from the phone to S3:
 * <ol>
 *   <li>{@link #createUploadUrl} - the app asks for a short-lived presigned PUT URL</li>
 *   <li>the app PUTs the JPEG to that URL (S3 checks the signature, size and content type)</li>
 *   <li>{@link #confirmUpload} - the app says it's done; we check S3 really has it, then mark UPLOADED</li>
 * </ol>
 * Photos only exist here after their inspection was submitted, which gives the
 * "record first, then its photos" order of FR-21. Every step is safe to repeat.
 */
@Service
public class PhotoService {

    private static final String CONTENT_TYPE = "image/jpeg";

    private final PhotoMapper photoMapper;
    private final InspectionMapper inspectionMapper;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public PhotoService(PhotoMapper photoMapper, InspectionMapper inspectionMapper, S3Client s3Client,
                        S3Presigner s3Presigner, S3Properties properties) {
        this.photoMapper = photoMapper;
        this.inspectionMapper = inspectionMapper;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    public UploadUrlResponse createUploadUrl(AuthUser user, UUID photoId, long sizeBytes) {
        Photo photo = findOwnPhoto(user, photoId);
        if (photo.getStatus() == PhotoStatus.UPLOADED) {
            return UploadUrlResponse.uploaded();
        }
        if (sizeBytes > properties.maxPhotoBytes()) {
            throw ApiException.badRequest("Photo is " + sizeBytes + " bytes; the limit is " + properties.maxPhotoBytes());
        }

        // Content type and length are signed into the URL: the app can only upload a JPEG of
        // exactly this size, to exactly this key, until the URL expires.
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(r -> r
                .signatureDuration(properties.presignTtl())
                .putObjectRequest(put -> put
                        .bucket(properties.bucket())
                        .key(photo.getS3Key())
                        .contentType(CONTENT_TYPE)
                        .contentLength(sizeBytes)));

        return new UploadUrlResponse(false, presigned.url().toString(), "PUT",
                Map.of("Content-Type", CONTENT_TYPE), presigned.expiration());
    }

    public PhotoStatusResponse confirmUpload(AuthUser user, UUID photoId) {
        Photo photo = findOwnPhoto(user, photoId);
        if (photo.getStatus() == PhotoStatus.UPLOADED) {
            return toResponse(photo);  // repeated confirm, e.g. the first response was lost
        }

        // Don't take the app's word for it - ask S3.
        HeadObjectResponse head = headObject(photo.getS3Key());
        if (head == null) {
            // Upload never finished (app killed mid-upload, URL expired...). The app should
            // request a new URL and upload again.
            throw ApiException.conflict("Photo file has not been uploaded yet");
        }
        if (head.contentLength() > properties.maxPhotoBytes()) {
            throw ApiException.badRequest("Uploaded photo is larger than " + properties.maxPhotoBytes() + " bytes");
        }

        // "AND status = PENDING" keeps the first uploaded_at if two confirms race.
        OffsetDateTime now = OffsetDateTime.now();
        photoMapper.update(Wrappers.<Photo>lambdaUpdate()
                .set(Photo::getStatus, PhotoStatus.UPLOADED)
                .set(Photo::getUploadedAt, now)
                .eq(Photo::getId, photoId)
                .eq(Photo::getStatus, PhotoStatus.PENDING));
        return toResponse(photoMapper.selectById(photoId));
    }

    /**
     * The photo, if it belongs to an inspection submitted by this user. Answers 404 both when it
     * doesn't exist and when it is someone else's, so photo ids can't be probed.
     */
    private Photo findOwnPhoto(AuthUser user, UUID photoId) {
        Photo photo = photoMapper.selectById(photoId);
        if (photo != null) {
            boolean own = inspectionMapper.exists(Wrappers.<Inspection>lambdaQuery()
                    .eq(Inspection::getId, photo.getInspectionId())
                    .eq(Inspection::getInspectorId, user.id()));
            if (own) {
                return photo;
            }
        }
        throw ApiException.notFound("Photo not found");
    }

    /** S3 metadata for the key, or null if there is no such object. */
    private HeadObjectResponse headObject(String key) {
        try {
            return s3Client.headObject(r -> r.bucket(properties.bucket()).key(key));
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw e;  // S3 down etc. -> 500, the app retries later
        }
    }

    private static PhotoStatusResponse toResponse(Photo photo) {
        return new PhotoStatusResponse(photo.getId(), photo.getStatus(), photo.getUploadedAt());
    }
}
