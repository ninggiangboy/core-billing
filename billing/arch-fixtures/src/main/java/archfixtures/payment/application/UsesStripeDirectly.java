package archfixtures.payment.application;

import com.stripe.model.Refund;

/** Violates stripe-types-stay-in-the-stripe-adapter: the application layer must see only the PaymentProvider port. */
public class UsesStripeDirectly {

    public String refundId(Refund refund) {
        return refund.getId();
    }
}
