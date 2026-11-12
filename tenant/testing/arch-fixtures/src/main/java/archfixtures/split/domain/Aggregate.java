package archfixtures.split.domain;

/** A domain aggregate that a read model must not use. */
public class Aggregate {
    String value() {
        return "";
    }
}
