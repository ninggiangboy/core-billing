package archfixtures.layout.application;

import org.springframework.data.relational.core.mapping.Table;

/** Violates table-rows-in-adapter-persistence: a @Table row outside adapter.persistence. */
@Table("misplaced_rows")
public class TableInApplication {}
