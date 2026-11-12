package dev.ngb.billing.money.api.wallet;
import dev.ngb.billing.money.api.ledger.LedgerPostingResult;

public interface CreditWallet {

    LedgerPostingResult credit(CreditCommand command);
}
