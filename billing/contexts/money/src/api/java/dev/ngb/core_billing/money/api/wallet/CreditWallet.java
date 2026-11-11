package dev.ngb.core_billing.money.api.wallet;
import dev.ngb.core_billing.money.api.ledger.LedgerPostingResult;

public interface CreditWallet {

    LedgerPostingResult credit(CreditCommand command);
}
