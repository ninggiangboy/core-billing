package io.corebilling.payment.application.incident.query;

import io.corebilling.sharedkernel.UnitOfWork;
import java.util.List;

/** Reads incidents for the GET handler. */
public class IncidentQueries {

    public static final int MAX_PAGE = 200;

    private final UnitOfWork unitOfWork;
    private final IncidentReadRepository incidents;

    public IncidentQueries(UnitOfWork unitOfWork, IncidentReadRepository incidents) {
        this.unitOfWork = unitOfWork;
        this.incidents = incidents;
    }

    public List<IncidentView> list(boolean openOnly, int limit) {
        return unitOfWork.transactional(() -> incidents.list(openOnly, Math.max(1, Math.min(limit, MAX_PAGE))));
    }
}
