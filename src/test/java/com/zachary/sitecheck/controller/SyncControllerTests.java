package com.zachary.sitecheck.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.zachary.sitecheck.TestcontainersConfiguration;
import java.time.OffsetDateTime;
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

/** Bootstrap download against the V2 seed data. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SyncControllerTests {

    private static final String AOIFE = "aoife.murphy@sitecheck.demo";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void inspectorGetsOnlyAssignedSites() throws Exception {
        mockMvc.perform(get("/api/sync/bootstrap").header("Authorization", bearer("liam.obrien@sitecheck.demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverTime").isNotEmpty())
                .andExpect(jsonPath("$.sites[*].name")
                        .value(contains("Cork Docklands Office Block", "Galway Student Residence")));
    }

    @Test
    void managerGetsAllSites() throws Exception {
        mockMvc.perform(get("/api/sync/bootstrap").header("Authorization", bearer("siobhan.kelly@sitecheck.demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sites", hasSize(4)));
    }

    @Test
    void templatesIncludeOrderedItemsAndCoverEverySiteReference() throws Exception {
        String body = mockMvc.perform(get("/api/sync/bootstrap")
                        .header("Authorization", bearer("liam.obrien@sitecheck.demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templates[?(@.name == 'Scaffold Inspection')].frequencyDays")
                        .value(contains(7)))
                .andExpect(jsonPath("$.templates[?(@.name == 'Scaffold Inspection')].items[*].position")
                        .value(contains(1, 2, 3, 4, 5, 6)))
                .andReturn().getResponse().getContentAsString();

        // Every template id a site points at must be in the templates list, or the app can't start that inspection.
        List<Integer> referenced = JsonPath.read(body, "$.sites[*].schedules[*].templateId");
        List<Integer> sent = JsonPath.read(body, "$.templates[*].id");
        assertThat(sent).containsAll(referenced);
    }

    @Test
    void schedulesCarryTheLatestInspectionPerSiteAndTemplate() throws Exception {
        // Cherrywood is Aoife's alone and no other test inspects it, so these are its only inspections.
        long cherrywood = jdbc.queryForObject(
                "SELECT id FROM sites WHERE name = 'Cherrywood Housing Phase 2'", Long.class);
        long excavation = templateId("Excavation Inspection");
        long safetyWalk = templateId("General Site Safety Walk");
        submitPassingInspection(AOIFE, cherrywood, excavation, "2026-10-01T09:30:00+01:00");
        submitPassingInspection(AOIFE, cherrywood, excavation, "2026-09-01T09:30:00+01:00");  // older, synced later

        String body = mockMvc.perform(get("/api/sync/bootstrap").header("Authorization", bearer(AOIFE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String schedules = "$.sites[?(@.id == " + cherrywood + ")].schedules[?(@.templateId == %d)].lastInspectedAt";
        List<String> excavationLast = JsonPath.read(body, schedules.formatted(excavation));
        List<String> safetyWalkLast = JsonPath.read(body, schedules.formatted(safetyWalk));

        // Latest by submittedAt (when it was done), not by upload order.
        assertThat(excavationLast).hasSize(1);
        assertThat(OffsetDateTime.parse(excavationLast.getFirst()).toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-10-01T09:30:00+01:00").toInstant());
        // Never inspected -> null, which the app shows as overdue.
        assertThat(safetyWalkLast).containsExactly((String) null);
    }

    @Test
    void requiresToken() throws Exception {
        mockMvc.perform(get("/api/sync/bootstrap"))
                .andExpect(status().isUnauthorized());
    }

    /** Valid inspection with a single PASS item (the server doesn't require every item answered). */
    private void submitPassingInspection(String email, long siteId, long templateId, String submittedAt)
            throws Exception {
        long itemId = jdbc.queryForObject(
                "SELECT id FROM template_items WHERE template_id = ? AND position = 1", Long.class, templateId);
        Map<String, Object> request = Map.of(
                "id", UUID.randomUUID(),
                "siteId", siteId,
                "templateId", templateId,
                "startedAt", submittedAt,
                "submittedAt", submittedAt,
                "signaturePng", "iVBORw0KGgo=",
                "results", List.of(Map.of("templateItemId", itemId, "result", "PASS")));
        mockMvc.perform(post("/api/inspections")
                        .header("Authorization", bearer(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private long templateId(String name) {
        return jdbc.queryForObject("SELECT id FROM inspection_templates WHERE name = ?", Long.class, name);
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
