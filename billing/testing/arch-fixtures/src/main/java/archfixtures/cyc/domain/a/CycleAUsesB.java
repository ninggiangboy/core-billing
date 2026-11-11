package archfixtures.cyc.domain.a;

import archfixtures.cyc.domain.b.CycleBUsesA;

/** Violates domain-features-are-free-of-cycles: feature a needs feature b, which needs a back. */
public class CycleAUsesB {
    CycleBUsesA other;
}
