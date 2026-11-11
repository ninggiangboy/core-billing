package dev.ngb.core_billing.money.adapter.ledger.persistence;

import static dev.ngb.core_billing_jooq.Tables.LEDGER_ENTRIES;
import static dev.ngb.core_billing_jooq.Tables.WALLETS;

import dev.ngb.core_billing.money.application.ledger.port.LedgerVerifier;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public class JooqLedgerVerifier implements LedgerVerifier {

    private final DSLContext dsl;

    public JooqLedgerVerifier(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<Mismatch> findMismatches() {
        var ledgerBalance = DSL.coalesce(DSL.sum(LEDGER_ENTRIES.AMOUNT).cast(Long.class), DSL.val(0L));
        var ledgerLastSequence = DSL.coalesce(DSL.max(LEDGER_ENTRIES.SEQ), DSL.val(0L));
        var entryCount = DSL.count(LEDGER_ENTRIES.ID).cast(Long.class);
        return dsl.select(WALLETS.ID, WALLETS.BALANCE, WALLETS.LAST_SEQUENCE, ledgerBalance, ledgerLastSequence, entryCount)
                .from(WALLETS)
                .leftJoin(LEDGER_ENTRIES)
                .on(LEDGER_ENTRIES.WALLET_ID.eq(WALLETS.ID))
                .groupBy(WALLETS.ID, WALLETS.BALANCE, WALLETS.LAST_SEQUENCE)
                .having(WALLETS.BALANCE.ne(ledgerBalance)
                        .or(WALLETS.LAST_SEQUENCE.ne(ledgerLastSequence))
                        .or(entryCount.ne(ledgerLastSequence)))
                .orderBy(WALLETS.ID)
                .fetch(row -> new Mismatch(
                        new WalletId(row.value1()),
                        row.value2(),
                        row.value4(),
                        row.value3(),
                        row.value5(),
                        row.value6()));
    }
}
