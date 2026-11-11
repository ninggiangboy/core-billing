package dev.ngb.core_billing.payment.adapter.stripe.persistence;

import static dev.ngb.core_billing_jooq.Tables.STRIPE_CONFIGURATIONS;
import static org.jooq.impl.DSL.field;

import dev.ngb.core_billing.payment.application.stripe.query.StripeConfigurationReadRepository;
import dev.ngb.core_billing.payment.application.stripe.query.StripeConfigurationView;
import dev.ngb.core_billing.payment.domain.stripe.ConnectionStatus;
import dev.ngb.core_billing.payment.domain.stripe.IntegrationMode;
import dev.ngb.core_billing.payment.domain.stripe.StripeEnvironment;
import java.util.Optional;
import org.jooq.DSLContext;

/** Selects the live Stripe configuration straight into the view; secrets are never selected. */
public class JooqStripeConfigurationReadRepository implements StripeConfigurationReadRepository {

    private final DSLContext dsl;

    public JooqStripeConfigurationReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<StripeConfigurationView> findLive() {
        return dsl.select(
                        STRIPE_CONFIGURATIONS.ID,
                        STRIPE_CONFIGURATIONS.MODE,
                        STRIPE_CONFIGURATIONS.ACCOUNT_ID,
                        STRIPE_CONFIGURATIONS.ENVIRONMENT,
                        STRIPE_CONFIGURATIONS.STATUS,
                        STRIPE_CONFIGURATIONS.PUBLISHABLE_KEY,
                        field(STRIPE_CONFIGURATIONS.WEBHOOK_SECRET_CIPHERTEXT.isNotNull()),
                        STRIPE_CONFIGURATIONS.CARD_PAYMENTS_ACTIVE,
                        STRIPE_CONFIGURATIONS.CREATED_AT,
                        STRIPE_CONFIGURATIONS.UPDATED_AT,
                        STRIPE_CONFIGURATIONS.CONNECTED_AT,
                        STRIPE_CONFIGURATIONS.LAST_VERIFIED_AT)
                .from(STRIPE_CONFIGURATIONS)
                .where(STRIPE_CONFIGURATIONS.STATUS.ne("DISCONNECTED"))
                .fetchOptional(row -> new StripeConfigurationView(
                        row.value1(),
                        IntegrationMode.valueOf(row.value2()),
                        row.value3(),
                        StripeEnvironment.valueOf(row.value4()),
                        ConnectionStatus.valueOf(row.value5()),
                        row.value6(),
                        row.value7(),
                        row.value8(),
                        row.value9(),
                        row.value10(),
                        row.value11(),
                        row.value12()));
    }
}
