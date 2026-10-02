package com.brokenworld.registry;

import com.brokenworld.BrokenWorld;
import com.brokenworld.item.NoteItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public class ModItems {
    /** The finale's maze notes (see NoteItem). */
    public static final Registered<Item> NOTE = register("note", new NoteItem(new Item.Properties().stacksTo(1)));

    public static final Registered<Item> SILHOUETTE_SPAWN_EGG = register("silhouette_spawn_egg",
            new SpawnEggItem(ModEntities.SILHOUETTE.get(), 0x050505, 0x1a1a1a, new Item.Properties()));

    private static Registered<Item> register(String name, Item item) {
        return new Registered<>(Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(BrokenWorld.MODID, name), item));
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.accept(SILHOUETTE_SPAWN_EGG.get()));
    }
}
