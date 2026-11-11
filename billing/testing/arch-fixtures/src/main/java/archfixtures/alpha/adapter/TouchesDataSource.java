package archfixtures.alpha.adapter;

import javax.sql.DataSource;

/** Violates the transaction rule: an adapter holding the datasource directly. */
public class TouchesDataSource {
    DataSource dataSource;
}
