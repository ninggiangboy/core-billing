package dev.ngb.tenant.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import dev.ngb.tenant.Application;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Spring Modulith owns module verification (cycles, access to a module's internals) and the
 * generated module documentation. Layer, purity and layout rules stay in ArchUnit.
 */
class ModulithVerificationTest {

    // boot is wiring and the HTTP boundary, and shared is the shared core every module may use;
    // neither is a bounded module. ArchUnit keeps the shared core independent.
    private final ApplicationModules modules = ApplicationModules.of(
            Application.class,
            JavaClass.Predicates.resideInAnyPackage("dev.ngb.tenant.boot..", "dev.ngb.tenant.shared.."));

    @Test
    void modulesAreWellFormed() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
