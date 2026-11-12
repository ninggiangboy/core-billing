package archfixtures.layout.application.feature.query;

import archfixtures.layout.api.PublicLookup;

/** Violates api-implementations-are-services: implements an api interface outside a service package, under another name. */
public class MisnamedLookupImplementation implements PublicLookup {}
