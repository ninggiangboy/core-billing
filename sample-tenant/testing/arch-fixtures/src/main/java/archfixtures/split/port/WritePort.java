package archfixtures.split.port;

/** Violates write-ports-serve-no-reads: a write port declares a list method. */
public interface WritePort {

    void append(String value);

    void listAll();
}
