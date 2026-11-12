package archfixtures.split.command;

import archfixtures.split.query.SomeView;

/** Violates write-side-does-not-depend-on-query: a command class reaches a query package. */
public class CommandReachesQuery {
    SomeView view;
}
