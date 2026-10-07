package com.brokenworld.mixin.client;

import com.brokenworld.client.AlphaBlocks;
import com.brokenworld.client.Hallucination;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** During a "Minecraft Alpha" hallucination, the mobs Alpha did not have are not drawn. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void brokenworld$alphaMobs(E entity, Frustum frustum, double x, double y, double z,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (Hallucination.active() && AlphaBlocks.hidden(entity)) cir.setReturnValue(false);
    }
}
