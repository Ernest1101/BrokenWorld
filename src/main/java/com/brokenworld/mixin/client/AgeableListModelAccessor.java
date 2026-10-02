package com.brokenworld.mixin.client;

import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** FacelessMobs: the head parts of animals' models (cows, pigs, chickens...). */
@Mixin(AgeableListModel.class)
public interface AgeableListModelAccessor {
    @Invoker("headParts")
    Iterable<ModelPart> brokenworld$headParts();
}
