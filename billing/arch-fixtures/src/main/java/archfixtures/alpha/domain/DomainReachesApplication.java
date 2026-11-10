package archfixtures.alpha.domain;

import archfixtures.alpha.application.PlainUseCase;

/** Violates layer direction: domain depends on application. */
public class DomainReachesApplication {
    PlainUseCase useCase;
}
