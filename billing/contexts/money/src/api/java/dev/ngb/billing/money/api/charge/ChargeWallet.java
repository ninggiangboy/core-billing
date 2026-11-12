package dev.ngb.billing.money.api.charge;

public interface ChargeWallet {

    ChargeResult charge(ChargeCommand command);
}
