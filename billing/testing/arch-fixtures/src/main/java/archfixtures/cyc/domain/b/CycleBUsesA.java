package archfixtures.cyc.domain.b;

import archfixtures.cyc.domain.a.CycleAUsesB;

/** The other half of the cycle between the two features. */
public class CycleBUsesA {
    CycleAUsesB other;
}
