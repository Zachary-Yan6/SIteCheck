package com.zachary.sitecheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zachary.sitecheck.config.S3Properties;
import com.zachary.sitecheck.dto.PhotoStatusResponse;
import com.zachary.sitecheck.dto.UploadUrlResponse;
import com.zachary.sitecheck.entity.Inspection;
import com.zachary.sitecheck.entity.Photo;
import com.zachary.sitecheck.entity.PhotoStatus;
import com.zachary.sitecheck.entity.Role;
import com.zachary.sitecheck.exception.ApiException;
import com.zachary.sitecheck.mapper.InspectionMapper;
import com.zachary.sitecheck.mapper.PhotoMapper;
import com.zachary.sitecheck.security.AuthUser;
import com.zachary.sitecheck.support.MybatisLambdaSupport;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/** Upload-URL and confirm steps with S3 mocked, including S3 answering 404 or failing. */
@ExtendWith(MockitoExtension.class)
class PhotoServiceUnitTests {

    private static final AuthUser AOIFE = new AuthUser(2L, "aoife@sitecheck.demo", Role.INSPECTOR);
    private static final long MAX_BYTES = 1_048_576;
    private static final S3Properties PROPERTIES = new S3Properties(
            "sitecheck-photos", "eu-west-1", null, null, Duration.ofMinutes(10), MAX_BYTES);

    @Mock
    private PhotoMapper photoMapper;
    @Mock
    private InspectionMapper inspectionMapper;
    @Mock
    private S3Client s3Client;
    @Mock
    private S3Presigner s3Presigner;
    private PhotoService service;

    private final UUID photoId = UUID.randomUUID();

    @BeforeAll
    static void registerEntities() {
        MybatisLambdaSupport.registerEntities(Inspection.class, Photo.class);
    }

    @BeforeEach
    void setUp() {
        service = new PhotoService(photoMapper, inspectionMapper, s3Client, s3Presigner, PROPERTIES);
    }

    @Test
    void unknownPhotoIsNotFound() {
        when(photoMapper.selectById(photoId)).thenReturn(null);
        expectStatus(() -> service.createUploadUrl(AOIFE, photoId, 100), HttpStatus.NOT_FOUND);
    }

    @Test
    void someoneElsesPhotoIsAlsoNotFound() {
        when(photoMapper.selectById(photoId)).thenReturn(photo(PhotoStatus.PENDING));
        when(inspectionMapper.exists(any())).thenReturn(false);
        expectStatus(() -> service.confirmUpload(AOIFE, photoId), HttpStatus.NOT_FOUND);
    }

    @Test
    void uploadUrlIsSignedForThePhotosKey() throws Exception {
        ownPhoto(PhotoStatus.PENDING);
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3.test/photos/x.jpg?sig=1").toURL());
        when(presigned.expiration()).thenReturn(Instant.parse("2026-10-13T09:40:00Z"));
        when(s3Presigner.presignPutObject(any(Consumer.class))).thenReturn(presigned);

        UploadUrlResponse response = service.createUploadUrl(AOIFE, photoId, 500_000);

        assertThat(response.alreadyUploaded()).isFalse();
        assertThat(response.url()).isEqualTo("https://s3.test/photos/x.jpg?sig=1");
        assertThat(response.method()).isEqualTo("PUT");
        assertThat(response.headers()).containsEntry("Content-Type", "image/jpeg");
    }

    @Test
    void alreadyUploadedPhotoNeedsNoUrl() {
        ownPhoto(PhotoStatus.UPLOADED);
        assertThat(service.createUploadUrl(AOIFE, photoId, 500_000).alreadyUploaded()).isTrue();
        verifyNoInteractions(s3Presigner);
    }

    @Test
    void photoOverTheSizeLimitGetsNoUrl() {
        ownPhoto(PhotoStatus.PENDING);
        expectStatus(() -> service.createUploadUrl(AOIFE, photoId, MAX_BYTES + 1), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(s3Presigner);
    }

    @Test
    void confirmMarksThePhotoUploadedOnceS3HasIt() {
        Photo uploaded = photo(PhotoStatus.UPLOADED);
        uploaded.setUploadedAt(OffsetDateTime.now());
        // First read finds it pending; the read after the update finds it uploaded.
        when(photoMapper.selectById(photoId)).thenReturn(photo(PhotoStatus.PENDING), uploaded);
        when(inspectionMapper.exists(any())).thenReturn(true);
        when(s3Client.headObject(any(Consumer.class))).thenReturn(HeadObjectResponse.builder().contentLength(400_000L).build());

        PhotoStatusResponse response = service.confirmUpload(AOIFE, photoId);

        assertThat(response.status()).isEqualTo(PhotoStatus.UPLOADED);
        verify(photoMapper).update(any());
    }

    @Test
    void repeatedConfirmDoesNotAskS3Again() {
        ownPhoto(PhotoStatus.UPLOADED);
        assertThat(service.confirmUpload(AOIFE, photoId).status()).isEqualTo(PhotoStatus.UPLOADED);
        verifyNoInteractions(s3Client);
    }

    @Test
    void confirmBeforeTheFileIsInS3IsConflict() {
        ownPhoto(PhotoStatus.PENDING);
        when(s3Client.headObject(any(Consumer.class))).thenThrow(s3Error(404));
        expectStatus(() -> service.confirmUpload(AOIFE, photoId), HttpStatus.CONFLICT);
        verify(photoMapper, never()).update(any());
    }

    @Test
    void s3OutageIsPassedOnSoTheAppRetriesLater() {
        ownPhoto(PhotoStatus.PENDING);
        when(s3Client.headObject(any(Consumer.class))).thenThrow(s3Error(503));
        assertThatThrownBy(() -> service.confirmUpload(AOIFE, photoId)).isInstanceOf(S3Exception.class);
    }

    @Test
    void oversizedUploadIsNotConfirmed() {
        ownPhoto(PhotoStatus.PENDING);
        when(s3Client.headObject(any(Consumer.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(MAX_BYTES + 1).build());
        expectStatus(() -> service.confirmUpload(AOIFE, photoId), HttpStatus.BAD_REQUEST);
        verify(photoMapper, never()).update(any());
    }

    // ---------------------------------------------------------------- helpers

    private void ownPhoto(PhotoStatus status) {
        when(photoMapper.selectById(photoId)).thenReturn(photo(status));
        when(inspectionMapper.exists(any())).thenReturn(true);
    }

    private Photo photo(PhotoStatus status) {
        Photo photo = new Photo();
        photo.setId(photoId);
        photo.setInspectionId(UUID.randomUUID());
        photo.setS3Key("photos/x.jpg");
        photo.setStatus(status);
        return photo;
    }

    private static S3Exception s3Error(int status) {
        return (S3Exception) S3Exception.builder().statusCode(status).message("S3 says " + status).build();
    }

    private static void expectStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(status));
    }
}
