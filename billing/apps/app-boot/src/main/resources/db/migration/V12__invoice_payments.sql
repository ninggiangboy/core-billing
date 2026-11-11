-- A payment can now settle an invoice instead of topping up a wallet.
--
-- An invoice payment is SUCCEEDED without a ledger entry of its own: the payment context only reports that the
-- money arrived (payment.succeeded), and invoicing credits the wallet and marks the invoice paid from that event.
-- The reference to the invoice is an id without a foreign key, because invoicing owns the invoice and the payment
-- context must not depend on its table.

alter table payments add column invoice_id uuid;

do $$
declare
    name text;
begin
    for name in
        select conname from pg_constraint
        where conrelid = 'payments'::regclass and contype = 'c'
          and (pg_get_constraintdef(oid) like '%purpose%' or pg_get_constraintdef(oid) like '%ledger_entry_id IS NOT NULL%')
    loop
        execute format('alter table payments drop constraint %I', name);
    end loop;
end
$$;

alter table payments add constraint payments_purpose_check check (purpose in ('TOP_UP', 'INVOICE'));
alter table payments add constraint payments_invoice_reference_check check ((purpose = 'INVOICE') = (invoice_id is not null));
-- A top-up succeeds exactly when it has credited the wallet; an invoice payment never carries a ledger entry.
alter table payments add constraint payments_ledger_entry_check
    check (case purpose when 'TOP_UP' then (status = 'SUCCEEDED') = (ledger_entry_id is not null) else ledger_entry_id is null end);

create index payments_invoice_idx on payments (invoice_id) where invoice_id is not null;
