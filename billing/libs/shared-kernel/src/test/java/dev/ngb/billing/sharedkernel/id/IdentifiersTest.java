package dev.ngb.billing.sharedkernel.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentifiersTest {

    @Test
    void roundTripsThroughText() {
        UUID uuid = UUID.randomUUID();
        assertThat(new CustomerId(uuid).toString()).isEqualTo(uuid.toString());
        assertThat(new WalletId(uuid).value()).isEqualTo(uuid);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new CustomerId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WalletId(null)).isInstanceOf(NullPointerException.class);
    }
}
