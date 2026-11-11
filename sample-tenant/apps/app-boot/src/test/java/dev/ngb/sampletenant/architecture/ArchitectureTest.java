package dev.ngb.sampletenant.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The layered build already hides Spring from inner layers; this also blocks a stray import. */
@AnalyzeClasses(packages = "dev.ngb.sampletenant", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainAndApplicationArePlainJava = noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..");

    @ArchTest
    static final ArchRule domainDoesNotSeeAdapters = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule applicationDoesNotSeeAdapters = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter..");
}
