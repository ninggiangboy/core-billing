package dev.ngb.tenant.billing.adapter.client.feign;

import dev.ngb.tenant.billing.adapter.client.http.HttpBillingSettings;
import feign.Feign;
import feign.MethodMetadata;
import feign.Request;
import feign.http2client.Http2Client;
import java.lang.reflect.Method;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Builds one Feign client for one credential, so the admin and tenant keys never share a proxy. */
public final class BillingFeignClientFactory {

    private BillingFeignClientFactory() {}

    public static FeignBillingClient create(HttpBillingSettings settings) {
        JsonMapper json = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        JacksonCodec codec = new JacksonCodec(json);
        BillingFeignApi api = Feign.builder()
                .client(new Http2Client())
                .contract(new EncodedSlashContract())
                .encoder(codec)
                .decoder(codec)
                .requestInterceptor(new BearerAuthInterceptor(settings.apiKey()))
                .errorDecoder(new BillingErrorDecoder(json))
                .retryer(new LinearRetryer(settings.maxAttempts(), settings.retryBackoff().toMillis()))
                .options(new Request.Options(settings.connectTimeout(), settings.readTimeout(), false))
                .target(BillingFeignApi.class, stripTrailingSlash(settings.baseUrl()));
        return new FeignBillingClient(api);
    }

    /**
     * Feign turns an encoded slash in a path value back into a real one, so an id such as {@code a/b}
     * would reach a different Billing path; keeping it encoded sends the id as one path segment.
     */
    private static final class EncodedSlashContract extends SpringMvcContract {

        @Override
        public MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {
            MethodMetadata metadata = super.parseAndValidateMetadata(targetType, method);
            metadata.template().decodeSlash(false);
            return metadata;
        }
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
