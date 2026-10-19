package com.zachary.sitecheck.job;

import com.zachary.sitecheck.service.DueCheck;
import com.zachary.sitecheck.service.ReminderService;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the reminders every morning (FR-28), at 07:00 Irish time by default - before inspectors set
 * off for site. One backend instance, so no ShedLock is needed; reminder_log still guarantees a
 * reminder goes out at most once a day even if the job runs twice.
 *
 * Switched off with {@code sitecheck.reminders.enabled=false} (the tests do this).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "sitecheck.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderJob {

    private final ReminderService reminderService;

    public ReminderJob(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(cron = "${sitecheck.reminders.cron:0 0 7 * * *}", zone = "Europe/Dublin")
    public void sendReminders() {
        reminderService.sendDailyReminders(LocalDate.now(DueCheck.SITE_ZONE));
    }
}
