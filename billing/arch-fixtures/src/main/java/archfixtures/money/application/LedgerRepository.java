package archfixtures.money.application;

/** Violates the append-only rule: a ledger repository that can remove an entry. */
public interface LedgerRepository {

    void append(String entry);

    void delete(String entryId);
}
