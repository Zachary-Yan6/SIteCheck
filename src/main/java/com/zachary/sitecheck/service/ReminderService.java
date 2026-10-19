package com.zachary.sitecheck.service;

import com.zachary.sitecheck.client.PushSender;
import com.zachary.sitecheck.client.PushSender.PushMessage;
import com.zachary.sitecheck.client.PushSender.PushTicket;
import com.zachary.sitecheck.mapper.DeviceTokenMapper;
import com.zachary.sitecheck.mapper.DeviceTokenMapper.UserToken;
import com.zachary.sitecheck.mapper.ReminderMapper;
import com.zachary.sitecheck.mapper.ReminderMapper.ScheduleRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Daily reminders (FR-28, FR-29): every inspector gets one notification per assigned site that has
 * a check overdue, due today or due tomorrow. Tapping it opens that site in the app.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    /** Android notification channel the app creates (see the app's notifications setup). */
    static final String ANDROID_CHANNEL = "reminders";

    private final ReminderMapper reminderMapper;
    private final DeviceTokenMapper deviceTokenMapper;
    private final PushSender pushSender;

    public ReminderService(ReminderMapper reminderMapper, DeviceTokenMapper deviceTokenMapper, PushSender pushSender) {
        this.reminderMapper = reminderMapper;
        this.deviceTokenMapper = deviceTokenMapper;
        this.pushSender = pushSender;
    }

    /**
     * Sends today's reminders and returns how many notifications Expo accepted. Safe to run more
     * than once a day: each (inspector, site) is claimed in reminder_log before sending.
     */
    public int sendDailyReminders(LocalDate today) {
        Map<Long, List<String>> tokensByUser = deviceTokenMapper.selectAll().stream()
                .collect(Collectors.groupingBy(UserToken::userId,
                        Collectors.mapping(UserToken::token, Collectors.toList())));

        List<PushMessage> messages = new ArrayList<>();
        List<SiteReminder> claimed = new ArrayList<>();
        for (SiteReminder reminder : dueReminders(today)) {
            List<String> tokens = tokensByUser.getOrDefault(reminder.userId(), List.of());
            if (tokens.isEmpty() || reminderMapper.claim(reminder.userId(), reminder.siteId(), today) == 0) {
                continue; // no phone to send to, or already reminded today
            }
            claimed.add(reminder);
            for (String token : tokens) {
                messages.add(reminder.toMessage(token));
            }
        }
        if (messages.isEmpty()) {
            return 0;
        }

        List<PushTicket> tickets;
        try {
            tickets = pushSender.send(messages);
        } catch (RuntimeException e) {
            // Expo unreachable: give the claims back so the next run (or a restart) tries again.
            claimed.forEach(r -> reminderMapper.release(r.userId(), r.siteId(), today));
            throw e;
        }

        int accepted = 0;
        for (int i = 0; i < tickets.size(); i++) {
            PushTicket ticket = tickets.get(i);
            if ("ok".equals(ticket.status())) {
                accepted++;
            } else if (ticket.deviceNotRegistered()) {
                deviceTokenMapper.delete(messages.get(i).to()); // app uninstalled - stop sending there
            } else {
                log.warn("Push to {} failed: {} {}", messages.get(i).to(), ticket.message(), ticket.details());
            }
        }
        log.info("Sent {} reminder(s) for {} site(s) on {}", accepted, claimed.size(), today);
        return accepted;
    }

    /** One entry per (inspector, site) with at least one check needing attention, most urgent first. */
    List<SiteReminder> dueReminders(LocalDate today) {
        Map<List<Long>, SiteReminder> bySite = new LinkedHashMap<>();
        for (ScheduleRow row : reminderMapper.selectSchedules()) {
            Optional<DueCheck> due = DueCheck.of(row.templateName(), row.frequencyDays(), row.lastInspectedAt(), today);
            if (due.isEmpty()) continue;
            bySite.computeIfAbsent(List.of(row.userId(), row.siteId()),
                            key -> new SiteReminder(row.userId(), row.siteId(), row.siteName(), new ArrayList<>()))
                    .checks().add(due.get());
        }
        bySite.values().forEach(r -> r.checks().sort(Comparator.comparingLong(DueCheck::urgency)));
        return List.copyOf(bySite.values());
    }

    record SiteReminder(Long userId, Long siteId, String siteName, List<DueCheck> checks) {

        /** E.g. title "Cork Docklands Office Block", body "Scaffold Inspection overdue by 3 days · Lifting Equipment Check due today". */
        PushMessage toMessage(String token) {
            String body = checks.stream().map(DueCheck::describe).collect(Collectors.joining(" · "));
            // FR-29: the app opens this route when the notification is tapped.
            Map<String, Object> data = Map.of("url", "/site/" + siteId, "siteId", siteId);
            return new PushMessage(token, siteName, body, data, ANDROID_CHANNEL, "default", "high");
        }
    }
}
