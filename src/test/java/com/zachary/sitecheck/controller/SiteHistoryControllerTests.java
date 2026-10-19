package com.zachary.sitecheck.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.zachary.sitecheck.TestcontainersConfiguration;
import java.util.ArrayList;
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

/**
 * Site history (FR-09) on Dublin Docklands, a site no other test class submits to, so the
 * history contains exactly what these tests put there.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SiteHistoryControllerTests {

    private static final String AOIFE = "aoife.murphy@sitecheck.demo";
    private static final String LIAM = "liam.obrien@sitecheck.demo";
    private static final String SIOBHAN = "siobhan.kelly@sitecheck.demo";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private static boolean submitted;

    /** Two inspections, once for the whole class: an older scaffold check with a FAIL, a newer all-PASS walk. */
    @BeforeEach
    void submitOnce() throws Exception {
        if (submitted) return;
        submit("Scaffold Inspection", "2026-06-01T09:00:00Z", 2, true);
        submit("General Site Safety Walk", "2026-06-05T09:00:00Z", 3, false);
        submitted = true;
    }

    @Test
    void assignedInspectorSeesHistoryNewestFirstWithFailures() throws Exception {
        mockMvc.perform(get("/api/sites/" + dublin() + "/inspections").header("Authorization", bearer(AOIFE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].templateName").value(contains("General Site Safety Walk", "Scaffold Inspection")))
                .andExpect(jsonPath("$[0].inspectorName").value("Aoife Murphy"))
                .andExpect(jsonPath("$[0].itemCount").value(3))
                .andExpect(jsonPath("$[0].failCount").value(0))
                .andExpect(jsonPath("$[0].failedItems", hasSize(0)))
                .andExpect(jsonPath("$[1].itemCount").value(2))
                .andExpect(jsonPath("$[1].failCount").value(1))
                .andExpect(jsonPath("$[1].failedItems").value(contains("Base plates and sole boards in place")));
    }

    @Test
    void inspectorsCannotSeeOtherSitesHistory() throws Exception {
        mockMvc.perform(get("/api/sites/" + dublin() + "/inspections").header("Authorization", bearer(LIAM)))
                .andExpect(status().isForbidden());
    }

    @Test
    void managersCanSeeAnySite() throws Exception {
        mockMvc.perform(get("/api/sites/" + dublin() + "/inspections").header("Authorization", bearer(SIOBHAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void limitIsRespected() throws Exception {
        mockMvc.perform(get("/api/sites/" + dublin() + "/inspections?limit=1").header("Authorization", bearer(AOIFE)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].templateName").value("General Site Safety Walk"));
    }

    // ---------------------------------------------------------------- helpers

    /** Answers the first `items` items PASS, except item 1 FAILs (with a photo) when `failFirst`. */
    private void submit(String templateName, String submittedAt, int items, boolean failFirst) throws Exception {
        long templateId = jdbc.queryForObject(
                "SELECT id FROM inspection_templates WHERE name = ?", Long.class, templateName);
        List<Long> itemIds = jdbc.queryForList(
                "SELECT id FROM template_items WHERE template_id = ? ORDER BY position LIMIT ?",
                Long.class, templateId, items);

        List<Map<String, Object>> results = new ArrayList<>();
        List<Map<String, Object>> photos = new ArrayList<>();
        for (int i = 0; i < itemIds.size(); i++) {
            if (i == 0 && failFirst) {
                results.add(Map.of("templateItemId", itemIds.get(i), "result", "FAIL", "note", "Sole board missing"));
                photos.add(Map.of("id", UUID.randomUUID(), "templateItemId", itemIds.get(i), "takenAt", submittedAt));
            } else {
                results.add(Map.of("templateItemId", itemIds.get(i), "result", "PASS"));
            }
        }
        Map<String, Object> request = Map.of(
                "id", UUID.randomUUID(),
                "siteId", dublin(),
                "templateId", templateId,
                "startedAt", submittedAt,
                "submittedAt", submittedAt,
                "signaturePng", "iVBORw0KGgo=",
                "results", results,
                "photos", photos);

        mockMvc.perform(post("/api/inspections")
                        .header("Authorization", bearer(AOIFE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private long dublin() {
        return jdbc.queryForObject("SELECT id FROM sites WHERE name = 'Dublin Docklands Apartments'", Long.class);
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
