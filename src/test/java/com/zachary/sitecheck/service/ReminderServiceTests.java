package com.zachary.sitecheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zachary.sitecheck.TestcontainersConfiguration;
import com.zachary.sitecheck.client.PushSender;
import com.zachary.sitecheck.client.PushSender.PushMessage;
import com.zachary.sitecheck.client.PushSender.PushTicket;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Daily reminders against the real database, with a fake PushSender instead of Expo. Replacing a
 * bean gives this class its own Spring context and so its own fresh database, where nothing has
 * been inspected yet. Each test uses its own "today" in mid-2027.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReminderServiceTests {

    private static final String LIAM_TOKEN = "ExponentPushToken[liam-phone]";

    @Autowired
    private ReminderService reminderService;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private PushSender pushSender;

    @BeforeEach
    void onlyLiamHasAPhone() {
        reset(pushSender);
        jdbc.update("DELETE FROM device_tokens");
        jdbc.update("INSERT INTO device_tokens (token, user_id, platform) VALUES (?, ?, 'android')",
                LIAM_TOKEN, userId("liam.obrien@sitecheck.demo"));
    }

    @Test
    void sendsOneNotificationPerAssignedSiteWithChecksDue() {
        acceptEverything();
        // Cork's scaffold check was last done on 20 May 2027: with a 7-day cycle it's 5 days overdue on 1 June.
        jdbc.update("""
                INSERT INTO inspections (id, site_id, template_id, inspector_id, started_at, submitted_at, signature_png)
                VALUES (gen_random_uuid(), ?, ?, ?, '2027-05-20T09:00:00Z', '2027-05-20T09:00:00Z', ?)
                """, siteId("Cork Docklands Office Block"), templateId("Scaffold Inspection"),
                userId("liam.obrien@sitecheck.demo"), new byte[] {0});

        int sent = reminderService.sendDailyReminders(LocalDate.of(2027, 6, 1));

        List<PushMessage> messages = sentMessages();
        // Liam works on Cork and Galway; Aoife has no registered phone, so nothing for her.
        assertThat(sent).isEqualTo(2);
        assertThat(messages).extracting(PushMessage::to).containsOnly(LIAM_TOKEN);
        assertThat(messages).extracting(PushMessage::title)
                .containsExactlyInAnyOrder("Cork Docklands Office Block", "Galway Student Residence");
        PushMessage cork = messages.stream().filter(m -> m.title().startsWith("Cork")).findFirst().orElseThrow();
        // Never inspected is the most urgent, so it comes first.
        assertThat(cork.body()).isEqualTo("Lifting Equipment Check never inspected · Scaffold Inspection overdue by 5 days");
        assertThat(cork.data()).containsEntry("url", "/site/" + siteId("Cork Docklands Office Block"));
        assertThat(cork.channelId()).isEqualTo("reminders");
    }

    @Test
    void remindsAtMostOncePerSiteAndDay() {
        acceptEverything();
        LocalDate day = LocalDate.of(2027, 6, 2);

        reminderService.sendDailyReminders(day);
        reset(pushSender);
        int secondRun = reminderService.sendDailyReminders(day);

        assertThat(secondRun).isZero();
        verify(pushSender, never()).send(anyList());
    }

    @Test
    void forgetsTokensOfUninstalledApps() {
        when(pushSender.send(anyList())).thenAnswer(call -> ((List<?>) call.getArgument(0)).stream()
                .map(m -> new PushTicket("error", null, "gone", Map.of("error", "DeviceNotRegistered")))
                .toList());

        reminderService.sendDailyReminders(LocalDate.of(2027, 6, 3));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_tokens WHERE token = ?", Integer.class, LIAM_TOKEN))
                .isZero();
    }

    @Test
    void whenExpoIsDownTheNextRunTriesAgain() {
        LocalDate day = LocalDate.of(2027, 6, 4);
        when(pushSender.send(anyList())).thenThrow(new IllegalStateException("Expo unreachable"));
        assertThatThrownBy(() -> reminderService.sendDailyReminders(day)).hasMessage("Expo unreachable");

        reset(pushSender);
        acceptEverything();
        int retry = reminderService.sendDailyReminders(day);

        assertThat(retry).isEqualTo(2);
    }

    // ---------------------------------------------------------------- helpers

    private void acceptEverything() {
        when(pushSender.send(anyList())).thenAnswer(call -> ((List<?>) call.getArgument(0)).stream()
                .map(m -> new PushTicket("ok", "ticket", null, null))
                .toList());
    }

    @SuppressWarnings("unchecked")
    private List<PushMessage> sentMessages() {
        ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(pushSender).send(captor.capture());
        return captor.getValue();
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long templateId(String name) {
        return jdbc.queryForObject("SELECT id FROM inspection_templates WHERE name = ?", Long.class, name);
    }

    private long siteId(String name) {
        return jdbc.queryForObject("SELECT id FROM sites WHERE name = ?", Long.class, name);
    }
}
