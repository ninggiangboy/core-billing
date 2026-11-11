-- The Platform owns its own data (administrators, roles, audit entries) as a reserved tenant, so
-- platform-level rows use the same row-level-security template and composite foreign keys as every
-- other tenant-scoped row.

insert into tenants (id, code, name, status, created_at)
values ('00000000-0000-7000-8000-000000000001', 'platform', 'Platform', 'ACTIVE', now());

-- audit_log: platform entries now carry the platform tenant instead of a null tenant.
drop policy audit_scope on audit_log;
update audit_log set tenant_id = '00000000-0000-7000-8000-000000000001' where tenant_id is null;
alter table audit_log drop constraint audit_log_check;
alter table audit_log drop column scope;
alter table audit_log alter column tenant_id set not null;
call enable_tenant_rls('audit_log');
