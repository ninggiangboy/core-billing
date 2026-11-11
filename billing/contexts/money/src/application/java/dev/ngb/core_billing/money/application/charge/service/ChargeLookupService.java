package dev.ngb.core_billing.money.application.charge.service;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.charge.ChargeLookup;
import dev.ngb.core_billing.money.api.charge.ChargeSummary;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import java.util.Optional;
import dev.ngb.core_billing.money.application.charge.query.ChargeReadRepository;

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
