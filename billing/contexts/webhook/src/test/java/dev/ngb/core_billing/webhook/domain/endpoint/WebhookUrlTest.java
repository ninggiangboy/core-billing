package dev.ngb.core_billing.webhook.domain.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookUrlTest {

    @Test
    void acceptsHttpsWithAPathAndQuery() {
        assertThat(WebhookUrl.parse("  https://hooks.example.com:8443/billing?x=1  ", false)).isEqualTo("https://hooks.example.com:8443/billing?x=1");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://hooks.example.com/x", "ftp://hooks.example.com/x", "//hooks.example.com/x", "hooks.example.com", "https:///x", "https://user:pw@hooks.example.com/x",
        "https://hooks.example.com/x#frag", "https://hooks.example.com:0/x", "https://127.0.0.1/x", "https://10.1.2.3/x", "https://[::1]/x",
        "https://169.254.169.254/latest/meta-data", "https://hooks.example.com/ spaced", ""})
    void refusesWhatIsNotAPublicHttpsDestination(String raw) {
        assertThatThrownBy(() -> WebhookUrl.parse(raw, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void developmentModeAllowsHttpAndLoopback() {
        assertThat(WebhookUrl.parse("http://127.0.0.1:9000/hook", true)).isEqualTo("http://127.0.0.1:9000/hook");
        assertThat(WebhookUrl.parse("http://localhost:9000/hook", true)).isEqualTo("http://localhost:9000/hook");
    }

    @Test
    void refusesOverlongUrls() {
        assertThatThrownBy(() -> WebhookUrl.parse("https://example.com/" + "a".repeat(2100), false)).isInstanceOf(IllegalArgumentException.class);
    }
}
