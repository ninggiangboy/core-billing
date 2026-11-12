package dev.ngb.billing.money.application.charge.service;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.money.api.charge.ChargeId;
import dev.ngb.billing.money.api.charge.ChargeLookup;
import dev.ngb.billing.money.api.charge.ChargeSummary;
import dev.ngb.billing.money.application.charge.query.ChargeReadRepository;
import java.util.Optional;

public class ChargeLookupService implements ChargeLookup {

    private final UnitOfWork unitOfWork;
    private final ChargeReadRepository charges;

    public ChargeLookupService(UnitOfWork unitOfWork, ChargeReadRepository charges) {
        this.unitOfWork = unitOfWork;
        this.charges = charges;
    }

    @Override
    public Optional<ChargeSummary> find(ChargeId id) {
        return unitOfWork.transactional(() -> charges.findById(id)
                .map(c -> new ChargeSummary(c.id(), c.walletId(), c.amount(), c.status(), c.source(), c.reference(), c.createdAt())));
    }
}
