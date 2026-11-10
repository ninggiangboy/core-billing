package archfixtures.alpha.domain;

import org.springframework.stereotype.Component;

/** Violates purity: a framework annotation in the domain layer. */
@Component
public class DomainUsesSpring {}
