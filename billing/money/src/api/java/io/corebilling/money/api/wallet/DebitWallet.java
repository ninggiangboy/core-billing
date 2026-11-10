package io.corebilling.money.api.wallet;
import io.corebilling.money.api.ledger.LedgerPostingResult;

public interface DebitWallet {

    /** @throws InsufficientFundsException when the wallet cannot cover the amount */
    LedgerPostingResult debit(DebitCommand command);
}
