package archfixtures.alpha.application;

import archfixtures.beta.domain.Secret;

/** Violates the module boundary: imports another module outside its api package. */
public class ReachesIntoBeta {
    Secret secret;
}
