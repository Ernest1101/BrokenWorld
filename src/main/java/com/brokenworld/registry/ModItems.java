package com.brokenworld.registry;

import com.brokenworld.BrokenWorld;
import com.brokenworld.item.NoteItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BrokenWorld.MODID);

    /** The finale's maze notes (see NoteItem). */
    public static final RegistryObject<Item> NOTE = ITEMS.register("note",
            () -> new NoteItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SILHOUETTE_SPAWN_EGG = ITEMS.register("silhouette_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SILHOUETTE, 0x050505, 0x1a1a1a, new Item.Properties()));
}
