plugins {
    id("layered-module-conventions")
}

dependencies {
    // The platform contract, the customer lookup, and the money contract: crediting a verified top-up, holding and taking back a refund.
    "domainImplementation"(project(":contexts:platform"))
    "applicationImplementation"(project(":contexts:platform"))
    "applicationImplementation"(project(":contexts:customer"))
    "applicationImplementation"(project(":contexts:money"))
    "adapterImplementation"(project(":contexts:platform"))
    "adapterImplementation"(project(":contexts:customer"))
    "adapterImplementation"(project(":contexts:money"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jooq")
    "adapterImplementation"(project(":jooq-schema"))
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-webmvc")
    "adapterImplementation"("org.springframework.boot:spring-boot-starter-jackson")
    // Stripe's own types stay inside payment.adapter.stripe; everything else sees the PaymentProvider port.
    "adapterImplementation"(libs.stripe.java)
    // The SDK exposes Gson types; the adapter also reads Stripe JSON with it.
    "adapterImplementation"("com.google.code.gson:gson")
    testImplementation("org.assertj:assertj-core")
}
