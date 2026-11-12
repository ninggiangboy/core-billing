package dev.ngb.billing.money.api.wallet;
import dev.ngb.billing.money.api.ledger.LedgerPostingResult;

public interface DebitWallet {

    /** @throws InsufficientFundsException when the wallet cannot cover the amount */
    LedgerPostingResult debit(DebitCommand command);
}
