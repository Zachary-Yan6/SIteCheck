package com.zachary.sitecheck.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.ResultActions;

/** Registering and removing push tokens (FR-27). Each test uses its own random token. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DeviceControllerTests {

    private static final String AOIFE = "aoife.murphy@sitecheck.demo";
    private static final String LIAM = "liam.obrien@sitecheck.demo";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registeringTwiceKeepsOneRow() throws Exception {
        String token = newToken();
        register(AOIFE, token).andExpect(status().isNoContent());
        register(AOIFE, token).andExpect(status().isNoContent());

        assertThat(ownerEmails(token)).containsExactly(AOIFE);
    }

    @Test
    void tokenMovesToWhoeverLoggedInOnThePhoneLast() throws Exception {
        String token = newToken();
        register(AOIFE, token);
        register(LIAM, token);

        assertThat(ownerEmails(token)).containsExactly(LIAM);
    }

    @Test
    void usersCanOnlyRemoveTheirOwnToken() throws Exception {
        String token = newToken();
        register(AOIFE, token);

        unregister(LIAM, token).andExpect(status().isNoContent());
        assertThat(ownerEmails(token)).containsExactly(AOIFE); // still there

        unregister(AOIFE, token).andExpect(status().isNoContent());
        assertThat(ownerEmails(token)).isEmpty();
    }

    @Test
    void rejectsSomethingThatIsNotAnExpoToken() throws Exception {
        register(AOIFE, "hello").andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- helpers

    private static String newToken() {
        return "ExponentPushToken[" + UUID.randomUUID() + "]";
    }

    private ResultActions register(String email, String token) throws Exception {
        return mockMvc.perform(post("/api/devices")
                .header("Authorization", bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\", \"platform\": \"android\"}".formatted(token)));
    }

    private ResultActions unregister(String email, String token) throws Exception {
        return mockMvc.perform(delete("/api/devices/{token}", token).header("Authorization", bearer(email)));
    }

    private java.util.List<String> ownerEmails(String token) {
        return jdbc.queryForList(
                "SELECT u.email FROM device_tokens d JOIN users u ON u.id = d.user_id WHERE d.token = ?",
                String.class, token);
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
