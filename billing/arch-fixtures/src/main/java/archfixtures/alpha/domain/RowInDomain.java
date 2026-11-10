package archfixtures.alpha.domain;

import org.springframework.data.relational.core.mapping.Table;

/** Violates persistence placement: a Spring Data row type outside adapter.persistence. */
@Table("orders")
public class RowInDomain {}
