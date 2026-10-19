package com.zachary.sitecheck.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.zachary.sitecheck.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Manager failed-items list. Each test submits at its own dates (early 2026, which no other test
 * class uses), so the shared database doesn't make results depend on test order.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ManagerControllerTests {

    private static final String LIAM = "liam.obrien@sitecheck.demo";
    private static final String SIOBHAN = "siobhan.kelly@sitecheck.demo";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void inspectorsCannotSeeTheManagerList() throws Exception {
        mockMvc.perform(get("/api/manager/failed-items").header("Authorization", bearer(LIAM)))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerSeesFailedItemsWithLinksToUploadedPhotosOnly() throws Exception {
        UUID uploaded = UUID.randomUUID();
        UUID stillPending = UUID.randomUUID();
        submitGalwayInspectionWithFail("2026-01-15T10:00:00Z", uploaded, stillPending);
        jdbc.update("UPDATE photos SET status = 'UPLOADED' WHERE id = ?", uploaded);

        mockMvc.perform(get("/api/manager/failed-items?from=2026-01-15&to=2026-01-15")
                        .header("Authorization", bearer(SIOBHAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))  // the PASS item is not listed
                .andExpect(jsonPath("$[0].siteName").value("Galway Student Residence"))
                .andExpect(jsonPath("$[0].templateName").value("Excavation Inspection"))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].note").value("Trench sides not supported"))
                .andExpect(jsonPath("$[0].inspectorName").value("Liam O'Brien"))
                .andExpect(jsonPath("$[0].photos", hasSize(1)))
                .andExpect(jsonPath("$[0].photos[0].id").value(uploaded.toString()))
                // Presigned GET link to the photo's key, with an expiry signature.
                .andExpect(jsonPath("$[0].photos[0].url").value(containsString(uploaded + ".jpg")))
                .andExpect(jsonPath("$[0].photos[0].url").value(containsString("X-Amz-Expires=")));
    }

    @Test
    void filtersBySite() throws Exception {
        submitGalwayInspectionWithFail("2026-02-10T10:00:00Z", UUID.randomUUID());
        long cork = jdbc.queryForObject("SELECT id FROM sites WHERE name = 'Cork Docklands Office Block'", Long.class);
        long galway = jdbc.queryForObject("SELECT id FROM sites WHERE name = 'Galway Student Residence'", Long.class);

        mockMvc.perform(get("/api/manager/failed-items?siteId=" + cork + "&from=2026-02-10&to=2026-02-10")
                        .header("Authorization", bearer(SIOBHAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/manager/failed-items?siteId=" + galway + "&from=2026-02-10&to=2026-02-10")
                        .header("Authorization", bearer(SIOBHAN)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void datesAreIrishCalendarDays() throws Exception {
        // 23:30 UTC on 30 March 2026 is 00:30 on 31 March in Ireland (summer time has started).
        submitGalwayInspectionWithFail("2026-03-30T23:30:00Z", UUID.randomUUID());

        mockMvc.perform(get("/api/manager/failed-items?from=2026-03-30&to=2026-03-30")
                        .header("Authorization", bearer(SIOBHAN)))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/manager/failed-items?from=2026-03-31&to=2026-03-31")
                        .header("Authorization", bearer(SIOBHAN)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void rejectsBackwardsOrHugeDateRanges() throws Exception {
        mockMvc.perform(get("/api/manager/failed-items?from=2026-02-01&to=2026-01-01")
                        .header("Authorization", bearer(SIOBHAN)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/manager/failed-items?from=2024-01-01&to=2026-01-01")
                        .header("Authorization", bearer(SIOBHAN)))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- helpers

    /** Liam's Galway excavation check: item 1 FAILs (with the given photos), item 2 PASSes. */
    private void submitGalwayInspectionWithFail(String submittedAt, UUID... photoIds) throws Exception {
        long galway = jdbc.queryForObject("SELECT id FROM sites WHERE name = 'Galway Student Residence'", Long.class);
        long excavation = jdbc.queryForObject(
                "SELECT id FROM inspection_templates WHERE name = 'Excavation Inspection'", Long.class);
        List<Long> items = jdbc.queryForList(
                "SELECT id FROM template_items WHERE template_id = ? ORDER BY position", Long.class, excavation);

        List<Map<String, Object>> photos = java.util.Arrays.stream(photoIds)
                .map(id -> Map.<String, Object>of("id", id, "templateItemId", items.get(0), "takenAt", submittedAt))
                .toList();
        Map<String, Object> request = Map.of(
                "id", UUID.randomUUID(),
                "siteId", galway,
                "templateId", excavation,
                "startedAt", submittedAt,
                "submittedAt", submittedAt,
                "signaturePng", "iVBORw0KGgo=",
                "results", List.of(
                        Map.of("templateItemId", items.get(0), "result", "FAIL", "note", "Trench sides not supported"),
                        Map.of("templateItemId", items.get(1), "result", "PASS")),
                "photos", photos);

        mockMvc.perform(post("/api/inspections")
                        .header("Authorization", bearer(LIAM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
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
