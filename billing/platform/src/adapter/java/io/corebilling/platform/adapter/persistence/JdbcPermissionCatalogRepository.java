package io.corebilling.platform.adapter.persistence;

import io.corebilling.platform.api.Permission;
import io.corebilling.platform.application.PermissionCatalogRepository;
import java.util.Collection;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcPermissionCatalogRepository implements PermissionCatalogRepository {

    private final JdbcClient jdbc;

    public JdbcPermissionCatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void sync(Collection<Permission> catalog) {
        for (Permission permission : catalog) {
            jdbc.sql("insert into permissions (code) values (?) on conflict do nothing").param(permission.code()).update();
        }
    }
}
