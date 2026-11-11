package archfixtures.money.application;

/** Violates the money rule: a use case that never depends on UnitOfWork. */
public class ChargeUseCase {
    public void charge() {}
}
