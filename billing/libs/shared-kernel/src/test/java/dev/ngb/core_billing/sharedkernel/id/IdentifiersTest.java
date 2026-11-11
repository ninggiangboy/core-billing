package dev.ngb.core_billing.sharedkernel.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;

class IdentifiersTest {

    @Test
    void roundTripsThroughText() {
        UUID uuid = UUID.randomUUID();
        assertThat(TenantId.of(uuid.toString())).isEqualTo(new TenantId(uuid));
        assertThat(new CustomerId(uuid).toString()).isEqualTo(uuid.toString());
        assertThat(new WalletId(uuid).value()).isEqualTo(uuid);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new TenantId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void unitOfWorkRunnableOverloadDelegates() {
        int[] calls = {0};
        UnitOfWork uow = new UnitOfWork() {
            @Override
            public <T> T transactional(java.util.function.Supplier<T> work) {
                calls[0]++;
                return work.get();
            }
        };
        uow.transactional(() -> calls[0] += 10);
        assertThat(calls[0]).isEqualTo(11);
    }
}
