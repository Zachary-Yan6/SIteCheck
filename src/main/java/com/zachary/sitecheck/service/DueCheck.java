package com.zachary.sitecheck.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * A check worth reminding about (FR-28): overdue, due today or due tomorrow. Same rules as the
 * app's home screen - Irish calendar days, never inspected counts as overdue.
 *
 * @param daysUntilDue negative = overdue by that many days; null = never inspected
 */
public record DueCheck(String templateName, Kind kind, Long daysUntilDue) {

    public enum Kind { OVERDUE, DUE_TODAY, DUE_TOMORROW }

    public static final ZoneId SITE_ZONE = ZoneId.of("Europe/Dublin");

    /** Empty when the check isn't due before the day after tomorrow - no reminder needed. */
    public static Optional<DueCheck> of(String templateName, int frequencyDays, OffsetDateTime lastInspectedAt,
                                        LocalDate today) {
        if (lastInspectedAt == null) {
            return Optional.of(new DueCheck(templateName, Kind.OVERDUE, null));
        }
        LocalDate dueDay = lastInspectedAt.atZoneSameInstant(SITE_ZONE).toLocalDate().plusDays(frequencyDays);
        long days = ChronoUnit.DAYS.between(today, dueDay);
        if (days < 0) return Optional.of(new DueCheck(templateName, Kind.OVERDUE, days));
        if (days == 0) return Optional.of(new DueCheck(templateName, Kind.DUE_TODAY, days));
        if (days == 1) return Optional.of(new DueCheck(templateName, Kind.DUE_TOMORROW, days));
        return Optional.empty();
    }

    /** Most urgent first: never inspected, then most overdue, then today, then tomorrow. */
    public long urgency() {
        return daysUntilDue == null ? Long.MIN_VALUE : daysUntilDue;
    }

    /** E.g. "Scaffold Inspection overdue by 3 days". */
    public String describe() {
        if (daysUntilDue == null) return templateName + " never inspected";
        return switch (kind) {
            case OVERDUE -> templateName + " overdue by " + (-daysUntilDue) + (daysUntilDue == -1 ? " day" : " days");
            case DUE_TODAY -> templateName + " due today";
            case DUE_TOMORROW -> templateName + " due tomorrow";
        };
    }
}
