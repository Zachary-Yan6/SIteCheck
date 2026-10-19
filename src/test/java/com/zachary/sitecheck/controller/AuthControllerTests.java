package com.zachary.sitecheck.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.zachary.sitecheck.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Login against the V2 seed users in a real Postgres (Testcontainers). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

    private static final String SEED_PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginWithValidCredentialsReturnsTokenAndUser() throws Exception {
        mockMvc.perform(login("aoife.murphy@sitecheck.demo", SEED_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.fullName").value("Aoife Murphy"))
                .andExpect(jsonPath("$.user.role").value("INSPECTOR"));
    }

    @Test
    void loginEmailIsCaseInsensitive() throws Exception {
        mockMvc.perform(login("Siobhan.Kelly@SiteCheck.demo", SEED_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("MANAGER"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveSame401() throws Exception {
        mockMvc.perform(login("aoife.murphy@sitecheck.demo", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        mockMvc.perform(login("nobody@sitecheck.demo", SEED_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void invalidRequestBodyGives400() throws Exception {
        mockMvc.perform(login("not-an-email", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meRequiresValidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCurrentUserForValidToken() throws Exception {
        String body = mockMvc.perform(login("liam.obrien@sitecheck.demo", SEED_PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("liam.obrien@sitecheck.demo"))
                .andExpect(jsonPath("$.fullName").value("Liam O'Brien"));
    }

    @Test
    void registerCreatesInspectorAndLogsIn() throws Exception {
        String email = uniqueEmail();
        String body = mockMvc.perform(register(email, "longEnough1", "New Inspector"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("INSPECTOR"))
                .andReturn().getResponse().getContentAsString();

        // The returned token works, and so does a normal login with the new password.
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + JsonPath.read(body, "$.token")))
                .andExpect(status().isOk());
        mockMvc.perform(login(email, "longEnough1"))
                .andExpect(status().isOk());
    }

    @Test
    void registerWithTakenEmailGives409IgnoringCase() throws Exception {
        mockMvc.perform(register("AOIFE.MURPHY@sitecheck.demo", "longEnough1", "Copycat"))
                .andExpect(status().isConflict());
    }

    @Test
    void registerValidatesInput() throws Exception {
        mockMvc.perform(register("not-an-email", "longEnough1", "Name"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(register(uniqueEmail(), "short", "Name"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(register(uniqueEmail(), "longEnough1", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void newInspectorSeesNoSites() throws Exception {
        String body = mockMvc.perform(register(uniqueEmail(), "longEnough1", "Unassigned"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/sync/bootstrap").header("Authorization", "Bearer " + JsonPath.read(body, "$.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sites").isEmpty());
    }

    private static String uniqueEmail() {
        return "new-" + java.util.UUID.randomUUID() + "@sitecheck.test";
    }

    private static org.springframework.test.web.servlet.RequestBuilder register(String email, String password,
                                                                                String fullName) {
        return post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "fullName": "%s"}
                        """.formatted(email, password, fullName));
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(String email, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password));
    }
}
