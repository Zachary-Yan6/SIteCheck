package com.zachary.sitecheck.dto;

import java.time.OffsetDateTime;

/**
 * One check a site needs, and when it was last done.
 *
 * <p>The app works out the due date itself: {@code lastInspectedAt + template.frequencyDays}, or
 * "overdue" if it was never inspected. Doing it on the phone (rather than sending a due date) keeps
 * the home screen sorting right while offline - days pass, and an inspection the user just submitted
 * but hasn't synced yet must reset the due date too (FR-08).
 *
 * @param lastInspectedAt submittedAt of the latest inspection that reached the server; null if never
 */
public record ScheduleDto(Long templateId, OffsetDateTime lastInspectedAt) {
}
