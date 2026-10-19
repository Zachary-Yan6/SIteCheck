package com.zachary.sitecheck.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zachary.sitecheck.service.DueCheck.Kind;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/** Same rules as the app's dueStatus.ts: Irish calendar days, never inspected = overdue. */
class DueCheckTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    @Test
    void neverInspectedIsOverdue() {
        assertThat(DueCheck.of("Scaffold", 7, null, TODAY))
                .hasValueSatisfying(d -> {
                    assertThat(d.kind()).isEqualTo(Kind.OVERDUE);
                    assertThat(d.describe()).isEqualTo("Scaffold never inspected");
                });
    }

    @Test
    void classifiesOverdueTodayTomorrowAndLater() {
        assertThat(DueCheck.of("Scaffold", 7, at("2026-10-03T10:00:00+01:00"), TODAY))
                .hasValueSatisfying(d -> assertThat(d.describe()).isEqualTo("Scaffold overdue by 3 days"));
        assertThat(DueCheck.of("Scaffold", 7, at("2026-10-05T10:00:00+01:00"), TODAY))
                .hasValueSatisfying(d -> assertThat(d.describe()).isEqualTo("Scaffold overdue by 1 day"));
        assertThat(DueCheck.of("Scaffold", 7, at("2026-10-06T10:00:00+01:00"), TODAY))
                .hasValueSatisfying(d -> assertThat(d.kind()).isEqualTo(Kind.DUE_TODAY));
        assertThat(DueCheck.of("Scaffold", 7, at("2026-10-07T10:00:00+01:00"), TODAY))
                .hasValueSatisfying(d -> assertThat(d.kind()).isEqualTo(Kind.DUE_TOMORROW));
        assertThat(DueCheck.of("Scaffold", 7, at("2026-10-08T10:00:00+01:00"), TODAY)).isEmpty();
    }

    @Test
    void usesTheIrishDayOfTheLastInspection() {
        // 23:30 UTC on 5 Oct is 00:30 on 6 Oct in Ireland, so the 7-day cycle ends on 13 Oct.
        assertThat(DueCheck.of("Scaffold", 7, at("2026-10-05T23:30:00Z"), TODAY))
                .hasValueSatisfying(d -> assertThat(d.kind()).isEqualTo(Kind.DUE_TODAY));
    }

    @Test
    void neverInspectedIsMoreUrgentThanAnyOverdue() {
        DueCheck never = DueCheck.of("A", 7, null, TODAY).orElseThrow();
        DueCheck longOverdue = DueCheck.of("B", 7, at("2025-01-01T10:00:00Z"), TODAY).orElseThrow();
        assertThat(never.urgency()).isLessThan(longOverdue.urgency());
    }

    @Test
    void describesChecksDueTodayAndTomorrow() {
        assertThat(DueCheck.of("Scaffold", 7, at("2026-10-06T10:00:00+01:00"), TODAY))
                .hasValueSatisfying(d -> assertThat(d.describe()).isEqualTo("Scaffold due today"));
        assertThat(DueCheck.of("Scaffold", 7, at("2026-10-07T10:00:00+01:00"), TODAY))
                .hasValueSatisfying(d -> assertThat(d.describe()).isEqualTo("Scaffold due tomorrow"));
    }

    private static OffsetDateTime at(String iso) {
        return OffsetDateTime.parse(iso);
    }
}
