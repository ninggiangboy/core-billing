package io.corebilling.architecture;

import io.corebilling.Application;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Spring Modulith owns module verification (cycles, access to a module's internals) and the
 * generated module documentation. Layer, purity and import rules stay in ArchUnit.
 */
class ModulithVerificationTest {

    private final ApplicationModules modules = ApplicationModules.of(Application.class);

    @Test
    void modulesAreWellFormed() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
