package dev.ngb.core.ddd;

import java.util.Objects;

/**
 * A DDD entity: it has an identity that outlives its attribute values, so two instances are the same entity when
 * they carry the same id, whatever else differs.
 */
public abstract class Entity<ID> {

    private final ID id;

    protected Entity(ID id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public final ID id() {
        return id;
    }

    @Override
    public final boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return id.equals(((Entity<?>) other).id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }
}
