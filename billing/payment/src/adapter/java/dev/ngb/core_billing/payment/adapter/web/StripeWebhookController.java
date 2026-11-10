package dev.ngb.core_billing.payment.adapter.web;

import dev.ngb.core_billing.payment.application.stripe.command.ReceiveStripeWebhookUseCase;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.actor.PublicEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Stripe delivers events. These endpoints take no login: the signature of the raw body is the proof, and
 * it is checked before anything in the body is read. The body is read as bytes, up to a fixed limit, so it reaches
 * the verifier exactly as Stripe sent it.
 */
@RestController
public class StripeWebhookController {

    /** Stripe events are a few kilobytes; anything near this is not one, and is refused before any work is done. */
    private static final int MAX_BODY_BYTES = 512 * 1024;

    private final ReceiveStripeWebhookUseCase receive;

    public StripeWebhookController(ReceiveStripeWebhookUseCase receive) {
        this.receive = receive;
    }

    /** A Direct webhook: the Tenant creates this endpoint in its own Stripe Dashboard and keeps the signing secret it is shown. */
    @PostMapping("/stripe/webhooks/direct/{configurationId}")
    @PublicEndpoint
    public Map<String, String> direct(
            @PathVariable String configurationId, @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            HttpServletRequest request) throws IOException {
        StripeConfigurationId id;
        try {
            id = StripeConfigurationId.of(configurationId);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("unknown webhook endpoint");
        }
        return answer(receive.receiveDirect(id, text(request), signature));
    }

    /** The Platform's Connect webhook, one endpoint for every connected account. */
    @PostMapping("/stripe/webhooks/connect")
    @PublicEndpoint
    public Map<String, String> connect(@RequestHeader(value = "Stripe-Signature", required = false) String signature, HttpServletRequest request)
            throws IOException {
        return answer(receive.receiveConnect(text(request), signature));
    }

    /** Reads at most one byte more than the limit, so an oversized body is refused without being held in memory. */
    private static String text(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("the body is too large to be a Stripe event");
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private static Map<String, String> answer(ReceiveStripeWebhookUseCase.Receipt receipt) {
        return Map.of("status", receipt.disposition().name().toLowerCase(java.util.Locale.ROOT));
    }
}
