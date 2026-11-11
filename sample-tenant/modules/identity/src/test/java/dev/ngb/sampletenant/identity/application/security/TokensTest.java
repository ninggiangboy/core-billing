package dev.ngb.sampletenant.identity.application.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokensTest {

    @Test
    void tokensAreOpaqueAndUnique() {
        String first = Tokens.generate();
        String second = Tokens.generate();

        assertThat(first).isNotEqualTo(second).hasSizeGreaterThan(30);
    }

    @Test
    void digestIsStableAndDoesNotRevealTheToken() {
        assertThat(Tokens.digest("secret")).isEqualTo(Tokens.digest("secret")).hasSize(64).doesNotContain("secret");
    }
}
