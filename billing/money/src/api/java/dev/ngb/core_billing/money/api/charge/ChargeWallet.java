package dev.ngb.core_billing.money.api.charge;

public interface ChargeWallet {

    ChargeResult charge(ChargeCommand command);
}
