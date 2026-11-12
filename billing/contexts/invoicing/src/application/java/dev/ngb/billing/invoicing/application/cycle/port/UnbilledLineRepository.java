package dev.ngb.billing.invoicing.application.cycle.port;

import dev.ngb.billing.invoicing.domain.cycle.UnbilledLine;
import dev.ngb.billing.sharedkernel.id.WalletId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnbilledLineRepository {

    /** One wallet's aggregation period that has ended and still holds lines no invoice covers. */
    record ClosedPeriod(WalletId walletId, Instant cycleStart, Instant cycleEnd) {}

    /** @return false when the charge already has a line (collected or billed before), so a repeated event adds nothing */
    boolean add(UnbilledLine line);

    /** The unbilled line of one charge, locked until the transaction ends; empty when the charge has no line yet or is already on an invoice. */
    Optional<UnbilledLine> lockByCharge(UUID chargeId);

    /** True when an invoice for this wallet's period already exists, so a late line must go to a later period. */
    boolean periodInvoiced(WalletId walletId, Instant cycleStart);

    /** Periods that ended at or before {@code now} and still hold unbilled lines, oldest first. */
    List<ClosedPeriod> closedPeriods(Instant now, int limit);

    /** The unbilled lines of one period, locked until the transaction ends. */
    List<UnbilledLine> lockPeriod(WalletId walletId, Instant cycleStart);
}
