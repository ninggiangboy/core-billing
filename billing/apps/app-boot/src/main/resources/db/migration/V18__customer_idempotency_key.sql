-- Move the customer idempotency key onto the row it created, and backfill it from the generic store.
--
-- Every tenant table forces row-level security, which also applies to the migration owner. The backfill
-- therefore lifts it inside this migration, or it would see no rows.

alter table customers add column idempotency_key text;
alter table customers add column request_hash text;

alter table customers no force row level security;
alter table idempotency_keys no force row level security;

update customers c
set idempotency_key = k.key,
    request_hash = k.request_hash
from idempotency_keys k
where k.tenant_id = c.tenant_id
  and k.scope = 'customer.create'
  and k.response_body::uuid = c.id;

-- A customer created before the store recorded a response, or whose entry was already gone, still needs a
-- key so the columns can be made required. Its key can never collide with a client-provided one.
update customers
set idempotency_key = 'legacy:' || id::text,
    request_hash = 'legacy'
where idempotency_key is null;

alter table customers force row level security;

alter table customers alter column idempotency_key set not null;
alter table customers alter column request_hash set not null;
alter table customers add constraint customers_tenant_id_idempotency_key_key unique (tenant_id, idempotency_key);
