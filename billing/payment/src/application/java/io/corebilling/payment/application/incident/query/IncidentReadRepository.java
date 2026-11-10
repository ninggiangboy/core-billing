package io.corebilling.payment.application.incident.query;

import java.util.List;

/** Read-only projection of incidents. */
public interface IncidentReadRepository {

    List<IncidentView> list(boolean openOnly, int limit);
}
