package io.corebilling.money.api;

public interface ChargeWallet {

    ChargeResult charge(ChargeCommand command);
}
