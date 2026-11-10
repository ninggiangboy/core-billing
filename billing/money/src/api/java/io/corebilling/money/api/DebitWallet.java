package io.corebilling.money.api;

public interface DebitWallet {

    /** @throws InsufficientFundsException when the wallet cannot cover the amount */
    LedgerPostingResult debit(DebitCommand command);
}
