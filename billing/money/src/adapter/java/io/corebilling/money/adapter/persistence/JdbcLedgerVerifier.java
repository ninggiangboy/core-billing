package io.corebilling.money.adapter.persistence;

import io.corebilling.money.application.ledger.command.LedgerVerifier;
import io.corebilling.sharedkernel.WalletId;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcLedgerVerifier implements LedgerVerifier {

    private final JdbcClient jdbc;

    public JdbcLedgerVerifier(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Mismatch> findMismatches() {
        return jdbc.sql("""
                        select w.id, w.balance, w.last_sequence,
                               coalesce(sum(l.amount), 0) as ledger_balance,
                               coalesce(max(l.seq), 0)    as ledger_last_sequence,
                               count(l.id)                as entry_count
                        from wallets w
                        left join ledger_entries l on l.wallet_id = w.id
                        group by w.id, w.balance, w.last_sequence
                        having w.balance <> coalesce(sum(l.amount), 0)
                            or w.last_sequence <> coalesce(max(l.seq), 0)
                            or count(l.id) <> coalesce(max(l.seq), 0)
                        order by w.id
                        """)
                .query((rs, n) -> new Mismatch(
                        new WalletId(Rows.uuid(rs, "id")), rs.getLong("balance"), rs.getLong("ledger_balance"),
                        rs.getLong("last_sequence"), rs.getLong("ledger_last_sequence"), rs.getLong("entry_count")))
                .list();
    }
}
