package archfixtures.split.query;

import archfixtures.split.port.WritePort;

/** Violates read-side-does-not-depend-on-write: a query class depends on a write port. */
public class QueryReachesWritePort {
    WritePort port;
}
