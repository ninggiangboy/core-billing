package io.corebilling.money.api;

public interface CreditWallet {

    LedgerPostingResult credit(CreditCommand command);
}
