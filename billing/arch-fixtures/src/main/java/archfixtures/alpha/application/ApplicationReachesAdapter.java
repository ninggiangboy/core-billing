package archfixtures.alpha.application;

import archfixtures.alpha.adapter.WebAdapter;

/** Violates layer direction: application depends on adapter. */
public class ApplicationReachesAdapter {
    WebAdapter adapter;
}
