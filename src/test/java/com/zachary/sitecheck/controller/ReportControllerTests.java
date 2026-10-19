package com.zachary.sitecheck.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.zachary.sitecheck.TestcontainersConfiguration;
import com.zachary.sitecheck.config.S3Properties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

/**
 * PDF report (FR-31) with a real photo in LocalStack S3 and a real PNG signature. Uses Galway in
 * May 2026, dates no other test class submits at.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerTests {

    private static final String LIAM = "liam.obrien@sitecheck.demo";
    private static final String SIOBHAN = "siobhan.kelly@sitecheck.demo";

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
        try {
            s3Client.createBucket(r -> r.bucket(s3Properties.bucket()));
        } catch (BucketAlreadyOwnedByYouException alreadyThere) {
            // created by an earlier test
        }
    }

    @Test
    void reportContainsResultsNotesPhotosAndSignature() throws Exception {
        UUID inspectionId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        submitGalwayInspection(inspectionId, photoId, "2026-05-04T10:00:00Z");
        // Put a real JPEG where the photo's key points and mark it uploaded, as the app would.
        String key = jdbc.queryForObject("SELECT s3_key FROM photos WHERE id = ?", String.class, photoId);
        s3Client.putObject(r -> r.bucket(s3Properties.bucket()).key(key).contentType("image/jpeg"),
                RequestBody.fromBytes(image("jpg")));
        jdbc.update("UPDATE photos SET status = 'UPLOADED' WHERE id = ?", photoId);

        byte[] pdf = mockMvc.perform(get("/api/manager/sites/" + galway() + "/report?from=2026-05-04&to=2026-05-04")
                        .header("Authorization", bearer(SIOBHAN)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"sitecheck-galway-student-residence-2026-05-04-to-2026-05-04.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();

        String text = pdfText(pdf);
        assertThat(text).contains("Site inspection report", "Galway Student Residence", "Excavation Inspection",
                "Trench sides not supported", "FAIL", "Pass", "Signed by Liam O'Brien", "1 failed item(s)");
        // Both images could be read and embedded.
        assertThat(text).doesNotContain("[photo unavailable]", "[signature could not be displayed]");
    }

    @Test
    void emptyPeriodStillGivesAValidReport() throws Exception {
        byte[] pdf = mockMvc.perform(get("/api/manager/sites/" + galway() + "/report?from=2026-05-20&to=2026-05-21")
                        .header("Authorization", bearer(SIOBHAN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(pdfText(pdf)).contains("No inspections were submitted for this site in this period.");
    }

    @Test
    void onlyManagersCanDownloadReports() throws Exception {
        mockMvc.perform(get("/api/manager/sites/" + galway() + "/report").header("Authorization", bearer(LIAM)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownSiteIsNotFound() throws Exception {
        mockMvc.perform(get("/api/manager/sites/999999/report").header("Authorization", bearer(SIOBHAN)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- helpers

    /** Excavation check at Galway: item 1 FAILs with one photo, item 2 PASSes, signed with a real PNG. */
    private void submitGalwayInspection(UUID inspectionId, UUID photoId, String submittedAt) throws Exception {
        long excavation = jdbc.queryForObject(
                "SELECT id FROM inspection_templates WHERE name = 'Excavation Inspection'", Long.class);
        List<Long> items = jdbc.queryForList(
                "SELECT id FROM template_items WHERE template_id = ? ORDER BY position", Long.class, excavation);

        Map<String, Object> request = Map.of(
                "id", inspectionId,
                "siteId", galway(),
                "templateId", excavation,
                "startedAt", submittedAt,
                "submittedAt", submittedAt,
                "signaturePng", Base64.getEncoder().encodeToString(image("png")),
                "results", List.of(
                        Map.of("templateItemId", items.get(0), "result", "FAIL", "note", "Trench sides not supported"),
                        Map.of("templateItemId", items.get(1), "result", "PASS")),
                "photos", List.of(Map.of("id", photoId, "templateItemId", items.get(0), "takenAt", submittedAt)));

        mockMvc.perform(post("/api/inspections")
                        .header("Authorization", bearer(LIAM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    /** A small solid-colour image in the given format ("jpg" or "png"). */
    private static byte[] image(String format) throws Exception {
        BufferedImage img = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 120, 80);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static String pdfText(byte[] pdf) throws Exception {
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        } finally {
            reader.close();
        }
    }

    private long galway() {
        return jdbc.queryForObject("SELECT id FROM sites WHERE name = 'Galway Student Residence'", Long.class);
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
