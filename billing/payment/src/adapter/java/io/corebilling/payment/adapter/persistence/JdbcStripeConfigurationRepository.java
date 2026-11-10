package io.corebilling.payment.adapter.persistence;

import io.corebilling.payment.application.stripe.port.StripeConfigurationRepository;
import io.corebilling.payment.domain.stripe.ConnectionStatus;
import io.corebilling.payment.domain.stripe.IntegrationMode;
import io.corebilling.payment.domain.stripe.StripeConfiguration;
import io.corebilling.payment.domain.stripe.StripeConfigurationId;
import io.corebilling.payment.domain.stripe.StripeEnvironment;
import io.corebilling.sharedkernel.TenantId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcStripeConfigurationRepository implements StripeConfigurationRepository {

    private static final String COLUMNS = "id, tenant_id, mode, account_id, environment, status, secret_key_ciphertext, webhook_secret_ciphertext, "
            + "publishable_key, card_payments_active, created_at, updated_at, connected_at, last_verified_at, disconnected_at";

    private static final RowMapper<StripeConfiguration> MAPPER = (rs, n) -> StripeConfiguration.restore(
            new StripeConfigurationId(Rows.uuid(rs, "id")),
            new TenantId(Rows.uuid(rs, "tenant_id")),
            IntegrationMode.valueOf(rs.getString("mode")),
            rs.getString("account_id"),
            StripeEnvironment.valueOf(rs.getString("environment")),
            ConnectionStatus.valueOf(rs.getString("status")),
            rs.getString("secret_key_ciphertext"),
            rs.getString("webhook_secret_ciphertext"),
            rs.getString("publishable_key"),
            rs.getBoolean("card_payments_active"),
            Rows.instant(rs, "created_at"),
            Rows.instant(rs, "updated_at"),
            Rows.instant(rs, "connected_at"),
            Rows.instant(rs, "last_verified_at"),
            Rows.instant(rs, "disconnected_at"));

    private final JdbcClient jdbc;

    public JdbcStripeConfigurationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(StripeConfiguration c) {
        jdbc.sql("insert into stripe_configurations (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(c.id().value(), c.tenantId().value(), c.mode().name(), c.accountId(), c.environment().name(), c.status().name(),
                        c.secretKeyCiphertext(), c.webhookSecretCiphertext(), c.publishableKey(), c.cardPaymentsActive(), Rows.timestamp(c.createdAt()),
                        Rows.timestamp(c.updatedAt()), Rows.timestamp(c.connectedAt()), Rows.timestamp(c.lastVerifiedAt()),
                        Rows.timestamp(c.disconnectedAt()))
                .update();
    }

    @Override
    public void update(StripeConfiguration c) {
        int updated = jdbc.sql("""
                        update stripe_configurations
                        set status = ?, webhook_secret_ciphertext = ?, card_payments_active = ?, updated_at = ?, connected_at = ?,
                            last_verified_at = ?, disconnected_at = ?
                        where id = ?
                        """)
                .params(c.status().name(), c.webhookSecretCiphertext(), c.cardPaymentsActive(), Rows.timestamp(c.updatedAt()),
                        Rows.timestamp(c.connectedAt()), Rows.timestamp(c.lastVerifiedAt()), Rows.timestamp(c.disconnectedAt()), c.id().value())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("stripe configuration " + c.id() + " was not updated");
        }
    }

    @Override
    public Optional<StripeConfiguration> findById(StripeConfigurationId id) {
        return jdbc.sql("select " + COLUMNS + " from stripe_configurations where id = ?").param(id.value()).query(MAPPER).optional();
    }

    @Override
    public Optional<StripeConfiguration> findLive() {
        return jdbc.sql("select " + COLUMNS + " from stripe_configurations where status <> 'DISCONNECTED'").query(MAPPER).optional();
    }

    @Override
    public List<StripeConfiguration> list() {
        return jdbc.sql("select " + COLUMNS + " from stripe_configurations order by created_at, id").query(MAPPER).list();
    }
}
