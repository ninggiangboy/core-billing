package archfixtures.layout.adapter.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Violates persistence-maps-without-row-mapper: maps a result set through a RowMapper. */
public class DomainRowMapper {

    public Object map(JdbcClient jdbc) {
        return jdbc.sql("select 1").query((rs, rowNum) -> rs.getString(1)).optional();
    }
}
