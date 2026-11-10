package io.corebilling.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import io.corebilling.Application;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Spring Modulith owns module verification (cycles, access to a module's internals) and the
 * generated module documentation. Layer, purity and import rules stay in ArchUnit.
 */
class ModulithVerificationTest {

    // io.corebilling.boot is wiring and the HTTP boundary, not a bounded context.
    private final ApplicationModules modules =
            ApplicationModules.of(Application.class, JavaClass.Predicates.resideInAPackage("io.corebilling.boot.."));

    @Test
    void modulesAreWellFormed() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
