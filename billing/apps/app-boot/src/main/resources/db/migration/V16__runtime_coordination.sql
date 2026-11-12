-- Coordination state shared by every instance. Nothing here is business data, and no row is locked while work runs:
-- a job lease is ownership by deadline, a run record is claimed before the work, and a rate-limit bucket is
-- touched for the length of one function call. Each statement touches one row, so these tables add no lock edge
-- in front of the wallet, invoice or payment rows.

-- One row per singleton job. Acquiring, renewing and releasing are short independent statements; the owner is
-- allowed back in at any time and another instance may take over once the deadline passes.
create table job_leases (
    name        text        primary key,
    owner       text        not null,
    acquired_at timestamptz not null,
    expires_at  timestamptz not null
);

-- A job that must run at most once per key (a day, a period) claims the run before doing the work. The primary
-- key is the promise: a concurrent or repeated claim inserts nothing. The tenant is a plain id rather than a
-- foreign key, so claiming a run never takes a lock on a business row.
create table job_runs (
    job        text        not null,
    run_key    text        not null,
    tenant_id  uuid        not null,
    started_at timestamptz not null,
    primary key (job, run_key, tenant_id)
);

-- One token bucket per API key id. A row is created on first use and removed once it has been idle long enough
-- that refilling it would return it to full burst anyway.
create table rate_limit_buckets (
    bucket_key text             primary key,
    tokens     double precision not null check (tokens >= 0),
    updated_at timestamptz      not null,
    expires_at timestamptz      not null
);

create index rate_limit_buckets_expiry_idx on rate_limit_buckets (expires_at);

-- One call takes one row lock for one key and answers whether the request is admitted. Keeping the decision in
-- one function call means the limiter can never run inside a business transaction by accident: it commits or
-- rolls back on its own.
create function try_acquire_rate_limit(
    p_key text,
    p_burst double precision,
    p_refill_per_second double precision,
    p_ttl_seconds integer
) returns boolean
    language plpgsql
as $$
declare
    v_now       timestamptz := clock_timestamp();
    v_tokens    double precision;
    v_updated   timestamptz;
    v_available double precision;
begin
    insert into rate_limit_buckets (bucket_key, tokens, updated_at, expires_at)
    values (p_key, p_burst, v_now, v_now + make_interval(secs => p_ttl_seconds))
    on conflict (bucket_key) do nothing;

    select tokens, updated_at into v_tokens, v_updated
    from rate_limit_buckets
    where bucket_key = p_key
    for update;

    v_available := least(
        p_burst,
        v_tokens + extract(epoch from (v_now - v_updated))::double precision * p_refill_per_second);

    if v_available >= 1 then
        update rate_limit_buckets
        set tokens = v_available - 1,
            updated_at = v_now,
            expires_at = v_now + make_interval(secs => p_ttl_seconds)
        where bucket_key = p_key;
        return true;
    end if;

    update rate_limit_buckets
    set tokens = v_available,
        updated_at = v_now,
        expires_at = v_now + make_interval(secs => p_ttl_seconds)
    where bucket_key = p_key;
    return false;
end;
$$;

grant execute on function try_acquire_rate_limit(text, double precision, double precision, integer) to billing_app;
