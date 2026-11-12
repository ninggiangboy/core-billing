plugins {
    id("layered-module-conventions")
}

dependencies {
    "applicationImplementation"(project(":modules:billing-client"))
    "applicationImplementation"(project(":modules:resource"))
    "applicationImplementation"(project(":modules:usage"))
    testImplementation(project(":modules:billing-client"))
    testImplementation(project(":modules:resource"))
    testImplementation(project(":modules:usage"))
    testImplementation("org.assertj:assertj-core")
}
