package com.zachary.sitecheck.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.zachary.sitecheck.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.test.web.servlet.ResultActions;

/** Submit endpoint against the V2 seed data. Each test uses fresh UUIDs, so tests don't interfere. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InspectionControllerTests {

    private static final String AOIFE = "aoife.murphy@sitecheck.demo";
    private static final String LIAM = "liam.obrien@sitecheck.demo";
    private static final String SIOBHAN = "siobhan.kelly@sitecheck.demo";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void retryAfterLostResponseStoresOnlyOneCopy() throws Exception {
        Map<String, Object> request = validScaffoldInspection(cork());
        String photoId = photoIdOf(request);

        String first = submit(AOIFE, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pendingPhotoIds").value(contains(photoId)))
                .andReturn().getResponse().getContentAsString();

        // Acceptance scenario 2: server saved it, the response got lost, the app sends it again.
        String retry = submit(AOIFE, request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(retry).isEqualTo(first);
        UUID id = UUID.fromString((String) request.get("id"));
        assertThat(count("SELECT count(*) FROM inspections WHERE id = ?", id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM inspection_results WHERE inspection_id = ?", id)).isEqualTo(6);
        assertThat(count("SELECT count(*) FROM photos WHERE inspection_id = ?", id)).isEqualTo(1);
    }

    @Test
    void inspectorCannotSubmitForSomeoneElsesSite() throws Exception {
        // Acceptance scenario 5: Galway is only assigned to Liam.
        Map<String, Object> request = validScaffoldInspection(siteId("Galway Student Residence"));
        submit(AOIFE, request).andExpect(status().isForbidden());
        assertThat(count("SELECT count(*) FROM inspections WHERE id = ?", UUID.fromString((String) request.get("id"))))
                .isZero();
    }

    @Test
    void managerCannotSubmit() throws Exception {
        submit(SIOBHAN, validScaffoldInspection(cork())).andExpect(status().isForbidden());
    }

    @Test
    void sameIdFromAnotherInspectorIsConflict() throws Exception {
        Map<String, Object> request = validScaffoldInspection(cork());  // Cork is assigned to both
        submit(LIAM, request).andExpect(status().isCreated());
        submit(AOIFE, request).andExpect(status().isConflict());
    }

    @Test
    void failWithoutPhotoIsRejected() throws Exception {
        Map<String, Object> request = validScaffoldInspection(cork());
        request.put("photos", List.of());
        submit(AOIFE, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.endsWith("failed but has no photo")));
    }

    @Test
    void itemFromAnotherTemplateIsRejected() throws Exception {
        Map<String, Object> request = validScaffoldInspection(cork());
        results(request).add(result(firstItemOf("Lifting Equipment Check"), "PASS", null));
        submit(AOIFE, request).andExpect(status().isBadRequest());
    }

    @Test
    void templateNotUsedAtSiteIsRejected() throws Exception {
        Map<String, Object> request = validScaffoldInspection(cork());
        request.put("templateId", templateId("Excavation Inspection"));  // Cork has no excavation checks
        submit(AOIFE, request).andExpect(status().isBadRequest());
    }

    @Test
    void missingFieldsAreRejected() throws Exception {
        Map<String, Object> request = validScaffoldInspection(cork());
        request.remove("signaturePng");
        submit(AOIFE, request).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- helpers

    /** Scaffold inspection: every item PASS except the first, which FAILs with a note and one photo. */
    private Map<String, Object> validScaffoldInspection(long siteId) {
        List<Long> itemIds = jdbc.queryForList(
                "SELECT id FROM template_items WHERE template_id = ? ORDER BY position",
                Long.class, templateId("Scaffold Inspection"));

        List<Map<String, Object>> results = new ArrayList<>();
        results.add(result(itemIds.getFirst(), "FAIL", "Sole board missing under standard"));
        itemIds.stream().skip(1).forEach(itemId -> results.add(result(itemId, "PASS", null)));

        Map<String, Object> photo = new HashMap<>();
        photo.put("id", UUID.randomUUID().toString());
        photo.put("templateItemId", itemIds.getFirst());
        photo.put("takenAt", "2026-10-13T09:15:00+01:00");
        photo.put("latitude", 51.9002);
        photo.put("longitude", -8.4537);

        Map<String, Object> request = new HashMap<>();
        request.put("id", UUID.randomUUID().toString());
        request.put("siteId", siteId);
        request.put("templateId", templateId("Scaffold Inspection"));
        request.put("startedAt", "2026-10-13T09:00:00+01:00");
        request.put("submittedAt", "2026-10-13T09:30:00+01:00");
        request.put("signaturePng", "iVBORw0KGgo=");  // base64 of the PNG file header, enough for the test
        request.put("results", results);
        request.put("photos", List.of(photo));
        return request;
    }

    private static Map<String, Object> result(long itemId, String result, String note) {
        Map<String, Object> map = new HashMap<>();
        map.put("templateItemId", itemId);
        map.put("result", result);
        map.put("note", note);
        return map;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> results(Map<String, Object> request) {
        return (List<Map<String, Object>>) request.get("results");
    }

    @SuppressWarnings("unchecked")
    private static String photoIdOf(Map<String, Object> request) {
        return (String) ((List<Map<String, Object>>) request.get("photos")).getFirst().get("id");
    }

    private ResultActions submit(String email, Map<String, Object> request) throws Exception {
        return mockMvc.perform(post("/api/inspections")
                .header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
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

    private long cork() {
        return siteId("Cork Docklands Office Block");
    }

    private long siteId(String name) {
        return jdbc.queryForObject("SELECT id FROM sites WHERE name = ?", Long.class, name);
    }

    private long templateId(String name) {
        return jdbc.queryForObject("SELECT id FROM inspection_templates WHERE name = ?", Long.class, name);
    }

    private long firstItemOf(String templateName) {
        return jdbc.queryForObject("SELECT min(i.id) FROM template_items i JOIN inspection_templates t "
                + "ON t.id = i.template_id WHERE t.name = ?", Long.class, templateName);
    }

    private int count(String sql, UUID id) {
        return jdbc.queryForObject(sql, Integer.class, id);
    }
}
