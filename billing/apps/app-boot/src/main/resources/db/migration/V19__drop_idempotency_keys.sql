-- The generic idempotency store is gone; every keyed flow keeps its key on the row it creates.
drop table idempotency_keys;
