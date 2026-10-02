package com.brokenworld.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * The mod's own data on any entity, saved with it ("BrokenWorldData"): which animals act like players, a player's game
 * mode from before the finale. Added to every entity by mixin.EntityDataMixin.
 */
public interface ModData {
    CompoundTag brokenworld$data();

    static CompoundTag of(Entity entity) {
        return ((ModData) entity).brokenworld$data();
    }
}
