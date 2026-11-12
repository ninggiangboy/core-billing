package dev.ngb.tenant.billing.adapter.client.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/** Attaches the credential of one Billing deployment; an empty key means the public admin surface. */
final class BearerAuthInterceptor implements RequestInterceptor {

    private final String apiKey;

    BearerAuthInterceptor(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (!apiKey.isBlank()) {
            template.header("Authorization", "Bearer " + apiKey);
        }
        if (!template.headers().containsKey("Accept")) {
            template.header("Accept", "application/json");
        }
    }
}
