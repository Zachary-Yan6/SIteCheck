package com.zachary.sitecheck.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zachary.sitecheck.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Managers assigning inspectors to sites. Uses freshly registered inspectors so seed assignments stay as they are. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SiteAssignmentControllerTests {

    private static final String LIAM = "liam.obrien@sitecheck.demo";
    private static final String SIOBHAN = "siobhan.kelly@sitecheck.demo";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void inspectorsCannotManageAssignments() throws Exception {
        long galway = siteId("Galway Student Residence");
        String liam = bearer(LIAM);
        mockMvc.perform(get("/api/manager/inspectors").header("Authorization", liam))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/manager/sites/" + galway + "/inspectors/1").header("Authorization", liam))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignedInspectorGetsTheSiteAndUnassignTakesItAway() throws Exception {
        NewInspector newbie = registerInspector();
        long cork = siteId("Cork Docklands Office Block");
        String manager = bearer(SIOBHAN);
        String path = "/api/manager/sites/" + cork + "/inspectors/" + newbie.id();

        mockMvc.perform(put(path).header("Authorization", manager)).andExpect(status().isNoContent());
        mockMvc.perform(put(path).header("Authorization", manager)).andExpect(status().isNoContent()); // repeat is fine

        mockMvc.perform(get("/api/manager/sites/" + cork + "/inspectors").header("Authorization", manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + newbie.id() + ")]", hasSize(1)));
        mockMvc.perform(get("/api/sync/bootstrap").header("Authorization", newbie.bearer()))
                .andExpect(jsonPath("$.sites", hasSize(1)))
                .andExpect(jsonPath("$.sites[0].name").value("Cork Docklands Office Block"));

        mockMvc.perform(delete(path).header("Authorization", manager)).andExpect(status().isNoContent());
        mockMvc.perform(delete(path).header("Authorization", manager)).andExpect(status().isNoContent()); // repeat is fine

        mockMvc.perform(get("/api/sync/bootstrap").header("Authorization", newbie.bearer()))
                .andExpect(jsonPath("$.sites").isEmpty());
    }

    @Test
    void rejectsManagersAndUnknownIds() throws Exception {
        long cork = siteId("Cork Docklands Office Block");
        long siobhanId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, SIOBHAN);
        NewInspector newbie = registerInspector();
        String manager = bearer(SIOBHAN);

        mockMvc.perform(put("/api/manager/sites/" + cork + "/inspectors/" + siobhanId).header("Authorization", manager))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/manager/sites/" + cork + "/inspectors/999999").header("Authorization", manager))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/manager/sites/999999/inspectors/" + newbie.id()).header("Authorization", manager))
                .andExpect(status().isNotFound());
    }

    @Test
    void inspectorListHasSignUpsButNoManagers() throws Exception {
        NewInspector newbie = registerInspector();
        mockMvc.perform(get("/api/manager/inspectors").header("Authorization", bearer(SIOBHAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + newbie.id() + ")]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.email == '" + SIOBHAN + "')]", hasSize(0)))
                .andExpect(jsonPath("$[*].role", everyItem(is("INSPECTOR"))));
    }

    private record NewInspector(long id, String bearer) {
    }

    private NewInspector registerInspector() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "assign-%s@sitecheck.test", "password": "longEnough1", "fullName": "New Inspector"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new NewInspector(((Number) JsonPath.read(body, "$.user.id")).longValue(),
                "Bearer " + JsonPath.read(body, "$.token"));
    }

    private long siteId(String name) {
        return jdbc.queryForObject("SELECT id FROM sites WHERE name = ?", Long.class, name);
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
