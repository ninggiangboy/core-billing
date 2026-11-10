package io.corebilling.money.api.wallet;
import io.corebilling.money.api.ledger.LedgerPostingResult;

public interface CreditWallet {

    LedgerPostingResult credit(CreditCommand command);
}
