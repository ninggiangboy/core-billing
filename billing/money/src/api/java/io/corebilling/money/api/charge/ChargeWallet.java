package io.corebilling.money.api.charge;

public interface ChargeWallet {

    ChargeResult charge(ChargeCommand command);
}
