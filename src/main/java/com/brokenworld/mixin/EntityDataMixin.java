package com.brokenworld.mixin;

import com.brokenworld.util.ModData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives every entity the mod's own saved data (util.ModData). */
@Mixin(Entity.class)
public abstract class EntityDataMixin implements ModData {
    @Unique
    private static final String BROKENWORLD_KEY = "BrokenWorldData";
    @Unique
    private CompoundTag brokenworld$data;

    @Override
    public CompoundTag brokenworld$data() {
        if (brokenworld$data == null) brokenworld$data = new CompoundTag();
        return brokenworld$data;
    }

    @Inject(method = "saveWithoutId", at = @At("HEAD"))
    private void brokenworld$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (brokenworld$data != null && !brokenworld$data.isEmpty()) tag.put(BROKENWORLD_KEY, brokenworld$data.copy());
    }

    @Inject(method = "load", at = @At("HEAD"))
    private void brokenworld$load(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(BROKENWORLD_KEY)) brokenworld$data = tag.getCompound(BROKENWORLD_KEY).copy();
    }
}
