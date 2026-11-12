package dev.ngb.tenant.billing.adapter.client.feign;

import dev.ngb.tenant.shared.error.BadRequestException;
import dev.ngb.tenant.shared.error.ConflictException;
import dev.ngb.tenant.shared.error.ForbiddenException;
import dev.ngb.tenant.shared.error.NotFoundException;
import dev.ngb.tenant.shared.error.PaymentRequiredException;
import dev.ngb.tenant.shared.error.UnauthorizedException;
import dev.ngb.tenant.shared.error.UpstreamUnavailableException;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Maps Billing's refusals onto the domain errors; 5xx and 429 stay retryable. */
final class BillingErrorDecoder implements ErrorDecoder {

    private final ObjectMapper json;

    BillingErrorDecoder(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        String detail = detail(response);
        if (status >= 500 || status == 429) {
            return new RetryableException(
                    status,
                    "Billing answered " + status + ": " + detail,
                    response.request().httpMethod(),
                    (Long) null,
                    response.request());
        }
        return switch (status) {
            case 401 -> new UnauthorizedException("Billing rejected the credential");
            case 402 -> new PaymentRequiredException(detail);
            case 403 -> new ForbiddenException("Billing refused this operation for the credential");
            case 404 -> new NotFoundException(detail);
            case 409 -> new ConflictException(detail);
            default -> status >= 400 && status < 500
                    ? new BadRequestException(detail)
                    : new UpstreamUnavailableException("Billing answered " + status + ": " + detail);
        };
    }

    private String detail(Response response) {
        if (response.body() == null) {
            return "the request was refused";
        }
        try {
            byte[] body = response.body().asInputStream().readAllBytes();
            if (body.length == 0) {
                return "the request was refused";
            }
            JsonNode node = json.readTree(body);
            String detail = node.path("detail").asString("");
            return detail.isBlank() ? node.path("title").asString("the request was refused") : detail;
        } catch (IOException | JacksonException e) {
            return "the request was refused";
        }
    }
}
