package com.brokenworld.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * One of the five notes hidden in the finale's maze. Which one is stored in the "Note" tag (0..4).
 * Hover over it to read it. They never despawn, so a note thrown past the pit cannot lock the game.
 */
public class NoteItem extends Item {
    public static final int COUNT = 5;
    private static final String TAG = "Note";

    public NoteItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Item item, int index) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putInt(TAG, index);
        return stack;
    }

    public static int index(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt(TAG) : 0;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.brokenworld.note", index(stack) + 1, COUNT);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int i = index(stack) + 1;
        tooltip.add(Component.translatable("note.brokenworld." + i + ".1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("note.brokenworld." + i + ".2").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
