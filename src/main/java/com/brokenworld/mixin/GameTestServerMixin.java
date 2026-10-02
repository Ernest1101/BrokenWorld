package com.brokenworld.mixin;

import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Only for the GameTests (gradlew runGametest): the vanilla test server makes its world without the dimensions from
 * data packs, so the mod's tunnel (finale, maze, hall, the bigger house) would be missing there. Real worlds have them.
 */
@Mixin(GameTestServer.class)
public abstract class GameTestServerMixin {
    @Redirect(method = "method_40377", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldDimensions;bake(Lnet/minecraft/core/Registry;)Lnet/minecraft/world/level/levelgen/WorldDimensions$Complete;"))
    private static WorldDimensions.Complete brokenworld$withDataPackDimensions(WorldDimensions dimensions, Registry<LevelStem> none,
                                                                                LevelSettings settings, WorldLoader.DataLoadContext context) {
        return dimensions.bake(context.datapackDimensions().registryOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM));
    }
}
