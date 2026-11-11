package archfixtures.split.domain;

/** Violates domain-id-names-its-aggregate: an id with no aggregate of the same name beside it. */
public record OrphanId(java.util.UUID value) {}
