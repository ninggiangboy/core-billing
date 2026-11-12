package dev.ngb.core.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.runtime.UnitOfWork;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class IdentifiersTest {

    @Test
    void roundTripsThroughText() {
        UUID uuid = UUID.randomUUID();
        assertThat(TenantId.of(uuid.toString())).isEqualTo(new TenantId(uuid));
        assertThat(new TenantId(uuid).toString()).isEqualTo(uuid.toString());
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
            public <T> T transactional(Supplier<T> work) {
                calls[0]++;
                return work.get();
            }
        };
        uow.transactional(() -> calls[0] += 10);
        assertThat(calls[0]).isEqualTo(11);
    }
}
