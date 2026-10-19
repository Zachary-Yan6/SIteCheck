package com.zachary.sitecheck.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.zachary.sitecheck.TestcontainersConfiguration;
import com.zachary.sitecheck.config.S3Properties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

/** Full presign -> PUT -> confirm flow against LocalStack S3. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PhotoControllerTests {

    private static final String AOIFE = "aoife.murphy@sitecheck.demo";
    private static final String LIAM = "liam.obrien@sitecheck.demo";
    private static final byte[] JPEG = new byte[2048];  // contents don't matter, only size and type

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private S3Client s3Client;
    @Autowired
    private S3Properties s3Properties;

    @BeforeEach
    void createBucket() {
        // docker-compose creates it with an init script; the test container starts empty.
        try {
            s3Client.createBucket(r -> r.bucket(s3Properties.bucket()));
        } catch (BucketAlreadyOwnedByYouException alreadyThere) {
            // created by an earlier test
        }
    }

    @Test
    void uploadThroughPresignedUrlThenConfirm() throws Exception {
        UUID inspectionId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        submitInspectionWithPhoto(AOIFE, inspectionId, photoId);

        String uploadUrl = requestUploadUrl(AOIFE, photoId, JPEG.length)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyUploaded").value(false))
                .andExpect(jsonPath("$.method").value("PUT"))
                .andReturn().getResponse().getContentAsString();

        assertThat(put(JsonPath.read(uploadUrl, "$.url"), JPEG)).isEqualTo(200);

        confirm(AOIFE, photoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.uploadedAt").isNotEmpty());

        // Confirm again (lost response) -> same answer; asking for a URL again -> "already uploaded".
        confirm(AOIFE, photoId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UPLOADED"));
        requestUploadUrl(AOIFE, photoId, JPEG.length)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyUploaded").value(true));

        // Retrying the inspection submit now reports no photos pending.
        submitInspectionWithPhoto(AOIFE, inspectionId, photoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingPhotoIds").isEmpty());
    }

    @Test
    void confirmBeforeUploadIsConflict() throws Exception {
        UUID photoId = UUID.randomUUID();
        submitInspectionWithPhoto(AOIFE, UUID.randomUUID(), photoId);

        // Acceptance scenario 3: app killed mid-upload, so S3 has nothing yet.
        confirm(AOIFE, photoId).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM photos WHERE id = ?", String.class, photoId))
                .isEqualTo("PENDING");
    }

    @Test
    void uploadOfDifferentSizeThanSignedIsRejectedByS3() throws Exception {
        UUID photoId = UUID.randomUUID();
        submitInspectionWithPhoto(AOIFE, UUID.randomUUID(), photoId);

        String uploadUrl = requestUploadUrl(AOIFE, photoId, 100).andReturn().getResponse().getContentAsString();

        assertThat(put(JsonPath.read(uploadUrl, "$.url"), JPEG)).isEqualTo(403);
    }

    @Test
    void tooLargePhotoIsRejected() throws Exception {
        UUID photoId = UUID.randomUUID();
        submitInspectionWithPhoto(AOIFE, UUID.randomUUID(), photoId);

        requestUploadUrl(AOIFE, photoId, s3Properties.maxPhotoBytes() + 1).andExpect(status().isBadRequest());
    }

    @Test
    void someoneElsesPhotoIsNotFound() throws Exception {
        UUID photoId = UUID.randomUUID();
        submitInspectionWithPhoto(LIAM, UUID.randomUUID(), photoId);

        requestUploadUrl(AOIFE, photoId, JPEG.length).andExpect(status().isNotFound());
        confirm(AOIFE, photoId).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- helpers

    /** Minimal valid inspection at Cork (assigned to both inspectors): one PASS item with one photo. */
    private ResultActions submitInspectionWithPhoto(String email, UUID inspectionId, UUID photoId) throws Exception {
        long siteId = jdbc.queryForObject(
                "SELECT id FROM sites WHERE name = 'Cork Docklands Office Block'", Long.class);
        long templateId = jdbc.queryForObject(
                "SELECT id FROM inspection_templates WHERE name = 'Scaffold Inspection'", Long.class);
        long itemId = jdbc.queryForObject(
                "SELECT id FROM template_items WHERE template_id = ? AND position = 1", Long.class, templateId);

        Map<String, Object> request = Map.of(
                "id", inspectionId,
                "siteId", siteId,
                "templateId", templateId,
                "startedAt", "2026-10-13T09:00:00+01:00",
                "submittedAt", "2026-10-13T09:30:00+01:00",
                "signaturePng", "iVBORw0KGgo=",
                "results", List.of(Map.of("templateItemId", itemId, "result", "PASS")),
                "photos", List.of(Map.of("id", photoId, "templateItemId", itemId,
                        "takenAt", "2026-10-13T09:15:00+01:00")));

        return mockMvc.perform(post("/api/inspections")
                        .header("Authorization", bearer(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is2xxSuccessful());
    }

    private ResultActions requestUploadUrl(String email, UUID photoId, long sizeBytes) throws Exception {
        return mockMvc.perform(post("/api/photos/{id}/upload-url", photoId)
                .header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sizeBytes\": " + sizeBytes + "}"));
    }

    private ResultActions confirm(String email, UUID photoId) throws Exception {
        return mockMvc.perform(post("/api/photos/{id}/confirm", photoId).header("Authorization", bearer(email)));
    }

    /** What the phone does: PUT the bytes straight to S3, with the signed Content-Type. */
    private int put(String url, byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "image/jpeg")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private String bearer(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Password123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }
}
