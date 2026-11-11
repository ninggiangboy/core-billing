-- A run record now records when the work finished. A claim whose process died stays unfinished, so a later tick can
-- take it over once it is older than the job's TTL instead of that tenant losing its run for the day.
alter table job_runs add column finished_at timestamptz;
