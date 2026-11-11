package dev.ngb.core_billing.invoicing.application.invoice.query;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceStatus;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.PageSize;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.util.List;
import java.util.UUID;

/** Read access to the current tenant's invoices, through the read models. */
public class InvoiceQueries {

    public record InvoiceDetail(InvoiceView invoice, List<InvoiceItemView> items, List<InvoicePaymentView> payments) {}

    private final UnitOfWork unitOfWork;
    private final InvoiceReadRepository invoices;
    private final CustomerLookup customers;

    public InvoiceQueries(UnitOfWork unitOfWork, InvoiceReadRepository invoices, CustomerLookup customers) {
        this.unitOfWork = unitOfWork;
        this.invoices = invoices;
        this.customers = customers;
    }

    /** A Tenant System sees only the invoices of the customers it mapped; any other caller sees the whole tenant. */
    public List<InvoiceView> list(UUID customerId, UUID walletId, InvoiceStatus status, UUID before, int limit, UUID caller) {
        return unitOfWork.transactional(() -> {
            List<UUID> visible = null;
            if (caller != null) {
                List<CustomerId> mapped = customers.mappedTo(caller);
                if (mapped.isEmpty()) {
                    return List.of();
                }
                visible = mapped.stream().map(CustomerId::value).toList();
            }
            return invoices.list(customerId, walletId, status, before, PageSize.of(limit), visible);
        });
    }

    public InvoiceDetail get(UUID id, UUID caller) {
        return unitOfWork.transactional(() -> {
            InvoiceView invoice = invoices.findById(id).orElseThrow(() -> new NotFoundException("invoice not found"));
            if (caller != null && customers.findFor(invoice.customerId(), caller).isEmpty()) {
                throw new NotFoundException("invoice not found");
            }
            return new InvoiceDetail(invoice, invoices.items(id), invoices.payments(id));
        });
    }
}
