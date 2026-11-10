package io.corebilling.platform.adapter.web;

import io.corebilling.platform.api.Actor;
import io.corebilling.platform.api.Authenticated;
import io.corebilling.platform.api.CurrentActor;
import io.corebilling.platform.api.Permission;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells a signed-in actor who Billing thinks it is. */
@RestController
@RequestMapping("/api/v1")
public class MeController {

    public record MeResponse(String type, String id, String tenantId, String scope, String displayName, Set<String> permissions) {}

    private final CurrentActor currentActor;

    public MeController(CurrentActor currentActor) {
        this.currentActor = currentActor;
    }

    @GetMapping("/me")
    @Authenticated
    public MeResponse me() {
        Actor actor = currentActor.get();
        return new MeResponse(
                actor.type().name(), actor.id().toString(), actor.tenantId().toString(), actor.scope().name(), actor.displayName(),
                actor.permissions().stream().map(Permission::code).collect(Collectors.toSet()));
    }
}
