package archfixtures.alpha.application;

import archfixtures.beta.domain.Secret;

/** Violates the context boundary: imports another context outside its api package. */
public class ReachesIntoBeta {
    Secret secret;
}
