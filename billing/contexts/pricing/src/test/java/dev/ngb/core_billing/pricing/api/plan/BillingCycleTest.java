package dev.ngb.core_billing.pricing.api.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BillingCycleTest {

    @Test
    void monthEndAnchorClampsInShortMonthsAndReturnsAfterwards() {
        Instant anchor = Instant.parse("2026-01-31T10:00:00Z");
        assertThat(BillingCycle.MONTHLY.plus(anchor, 1)).isEqualTo(Instant.parse("2026-02-28T10:00:00Z"));
        assertThat(BillingCycle.MONTHLY.plus(anchor, 2)).isEqualTo(Instant.parse("2026-03-31T10:00:00Z"));
    }

    @Test
    void leapDayAnchorRenewsOnTheLastDayOfFebruaryInOtherYears() {
        Instant anchor = Instant.parse("2028-02-29T00:00:00Z");
        assertThat(BillingCycle.YEARLY.plus(anchor, 1)).isEqualTo(Instant.parse("2029-02-28T00:00:00Z"));
        assertThat(BillingCycle.YEARLY.plus(anchor, 4)).isEqualTo(Instant.parse("2032-02-29T00:00:00Z"));
    }

    @Test
    void calendarPeriodsAreAlignedInUtc() {
        Instant at = Instant.parse("2026-11-11T23:59:59Z");
        assertThat(BillingCycle.DAILY.calendarStart(at)).isEqualTo(Instant.parse("2026-11-11T00:00:00Z"));
        assertThat(BillingCycle.WEEKLY.calendarStart(at)).isEqualTo(Instant.parse("2026-11-09T00:00:00Z"));
        assertThat(BillingCycle.MONTHLY.calendarStart(at)).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(BillingCycle.QUARTERLY.calendarStart(at)).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(BillingCycle.YEARLY.calendarStart(at)).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(BillingCycle.MONTHLY.calendarEnd(Instant.parse("2026-12-01T00:00:00Z"))).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
    }
}
