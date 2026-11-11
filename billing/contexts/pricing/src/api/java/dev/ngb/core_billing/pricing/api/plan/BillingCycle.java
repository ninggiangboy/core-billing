package dev.ngb.core_billing.pricing.api.plan;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * How often a plan bills. All period arithmetic is in UTC, so a period never depends on a server or customer
 * time zone. Periods are counted from an anchor, not chained, so a subscription anchored on the 31st renews on the
 * last day of a short month and returns to the 31st afterwards instead of drifting to the 28th.
 */
public enum BillingCycle {
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY;

    /** The instant {@code periods} whole cycles after {@code anchor}; month-based cycles clamp to the end of a short month. */
    public Instant plus(Instant anchor, long periods) {
        ZonedDateTime at = anchor.atZone(ZoneOffset.UTC);
        return switch (this) {
            case DAILY -> at.plusDays(periods).toInstant();
            case WEEKLY -> at.plusWeeks(periods).toInstant();
            case MONTHLY -> at.plusMonths(periods).toInstant();
            case QUARTERLY -> at.plusMonths(3 * periods).toInstant();
            case YEARLY -> at.plusYears(periods).toInstant();
        };
    }

    /** The start of the calendar period (day, ISO week, month, quarter or year, in UTC) that contains {@code at}. */
    public Instant calendarStart(Instant at) {
        ZonedDateTime day = at.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
        return switch (this) {
            case DAILY -> day.toInstant();
            case WEEKLY -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toInstant();
            case MONTHLY -> day.withDayOfMonth(1).toInstant();
            case QUARTERLY -> day.withDayOfMonth(1).withMonth((day.getMonthValue() - 1) / 3 * 3 + 1).toInstant();
            case YEARLY -> day.withDayOfYear(1).toInstant();
        };
    }

    /** The start of the calendar period after the one that starts at {@code calendarStart}. */
    public Instant calendarEnd(Instant calendarStart) {
        return plus(calendarStart, 1);
    }
}
