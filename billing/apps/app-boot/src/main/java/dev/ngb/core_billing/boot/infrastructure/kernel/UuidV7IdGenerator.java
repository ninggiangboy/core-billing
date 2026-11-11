package dev.ngb.core_billing.boot.infrastructure.kernel;

import dev.ngb.core_billing.sharedkernel.IdGenerator;
import java.security.SecureRandom;
import java.util.UUID;

/** Time-ordered UUIDs (version 7), which keep B-tree indexes append-mostly. */
public class UuidV7IdGenerator implements IdGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public UUID newId() {
        long millis = System.currentTimeMillis();
        long randA = random.nextLong() & 0x0FFFL;
        long randB = random.nextLong() & 0x3FFFFFFFFFFFFFFFL;
        long msb = (millis << 16) | 0x7000L | randA;
        long lsb = 0x8000000000000000L | randB;
        return new UUID(msb, lsb);
    }
}
