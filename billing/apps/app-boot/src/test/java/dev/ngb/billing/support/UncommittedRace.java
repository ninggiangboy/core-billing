package dev.ngb.billing.support;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.runtime.UnitOfWork;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Makes a race between two identical requests deterministic. The first request runs inside a transaction that stays
 * open until the second one is seen waiting on a row lock in PostgreSQL. By then the second request has missed the
 * first one's uncommitted row in its own reads and reached the statement that blocks on it, which is the path a
 * thread pool only hits by luck.
 */
public final class UncommittedRace {

    private static final Duration PATIENCE = Duration.ofSeconds(20);

    /** What each request returned. */
    public record Outcome<A, B>(A first, B second) {}

    private UncommittedRace() {}

    /**
     * @param waitingOn an {@code ilike} pattern for the statement the second request must be blocked in, such as
     *     {@code insert into %"payments"%}
     */
    public static <A, B> Outcome<A, B> run(
            UnitOfWork unitOfWork, ScopeTestSupport scopes, OwnerDatabase owner, TenantId tenant, String waitingOn,
            Supplier<A> first, Supplier<B> second) throws Exception {
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<A> one = pool.submit(() -> scopes.inTenant(tenant, () -> unitOfWork.transactional(() -> {
                A result = first.get();
                written.countDown();
                awaitQuietly(release);
                return result;
            })));
            Instant deadline = Instant.now().plus(PATIENCE);
            while (!written.await(50, TimeUnit.MILLISECONDS)) {
                if (one.isDone()) {
                    one.get();
                }
                if (Instant.now().isAfter(deadline)) {
                    throw new AssertionError("the first request did not finish its work in " + PATIENCE);
                }
            }

            Future<B> two = pool.submit(second::get);
            while (waitingOnLock(owner, waitingOn) == 0) {
                if (two.isDone()) {
                    release.countDown();
                    throw new AssertionError("the second request finished without waiting for the first one's row: " + describe(two));
                }
                if (Instant.now().isAfter(deadline)) {
                    throw new AssertionError("the second request never blocked in a statement like " + waitingOn);
                }
                Thread.sleep(20);
            }
            release.countDown();
            return new Outcome<>(one.get(PATIENCE.toSeconds(), TimeUnit.SECONDS), two.get(PATIENCE.toSeconds(), TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static long waitingOnLock(OwnerDatabase owner, String waitingOn) {
        return owner.count("select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock' and query ilike ?",
                waitingOn);
    }

    private static String describe(Future<?> done) {
        try {
            return String.valueOf(done.get());
        } catch (ExecutionException e) {
            return e.getCause().toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(PATIENCE.toSeconds(), TimeUnit.SECONDS)) {
                throw new IllegalStateException("the race was never released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
