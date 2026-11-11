package dev.ngb.core_billing.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import dev.ngb.core_billing.Application;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Spring Modulith owns module verification (cycles, access to a module's internals) and the
 * generated module documentation. Layer, purity and import rules stay in ArchUnit.
 */
class ModulithVerificationTest {

    // dev.ngb.core_billing.boot is wiring and the HTTP boundary, not a bounded context.
    private final ApplicationModules modules =
            ApplicationModules.of(Application.class, JavaClass.Predicates.resideInAPackage("dev.ngb.core_billing.boot.."));

    @Test
    void modulesAreWellFormed() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
