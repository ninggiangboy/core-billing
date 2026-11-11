-- A subscription whose period could not be paid after the bounded number of attempts stays PAST_DUE and has no
-- next attempt. Only a canceled subscription must have none, so the constraint that tied the two together is relaxed.

do $$
declare
    name text;
begin
    for name in
        select conname from pg_constraint
        where conrelid = 'subscriptions'::regclass and contype = 'c' and pg_get_constraintdef(oid) like '%next_renewal_at IS NULL%'
    loop
        execute format('alter table subscriptions drop constraint %I', name);
    end loop;
end
$$;

alter table subscriptions add constraint subscriptions_next_renewal_check
    check (case status when 'CANCELED' then next_renewal_at is null when 'ACTIVE' then next_renewal_at is not null else true end);
