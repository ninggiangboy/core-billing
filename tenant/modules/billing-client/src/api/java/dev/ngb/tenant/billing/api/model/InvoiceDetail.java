package dev.ngb.tenant.billing.api.model;

import java.util.List;

/** An invoice with its lines and payment history. */
public record InvoiceDetail(Invoice invoice, List<InvoiceItem> items, List<InvoicePayment> payments) {}
