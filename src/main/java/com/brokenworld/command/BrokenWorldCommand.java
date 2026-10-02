package com.brokenworld.command;

import com.brokenworld.entity.SilhouetteEntity;
import com.brokenworld.entity.SilhouetteEntity.Mode;
import com.brokenworld.network.ModNetwork;
import com.brokenworld.network.ModNetwork.ScreenFxPacket;
import com.brokenworld.registry.ModEntities;
import com.brokenworld.world.BrokenWorldState;
import com.brokenworld.world.Director;
import com.brokenworld.world.Finale;
import com.brokenworld.world.PlayerLikeAnimals;
import com.brokenworld.world.WorldGlitches;
import com.brokenworld.world.SpawnFinder;
import com.brokenworld.world.WorldEvents;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Debug / testing command (op level 2):
 * /brokenworld info | stage <0-4> | finale [stop] | screamer | summon [mode] | inspect [eyes] | clear | corruption <0-1|reset> | event <type>
 * /brokenworld freeze [on|off]: silhouettes from inspect / pose stop turning after you
 */
public final class BrokenWorldCommand {
    private BrokenWorldCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("brokenworld")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("info").executes(BrokenWorldCommand::info))
                .then(Commands.literal("stage")
                        .then(Commands.argument("stage", IntegerArgumentType.integer(0, BrokenWorldState.MAX_STAGE))
                                .executes(ctx -> setStage(ctx, IntegerArgumentType.getInteger(ctx, "stage")))))
                .then(Commands.literal("summon")
                        .executes(ctx -> summon(ctx, null))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Mode.values()).map(m -> m.name().toLowerCase(Locale.ROOT)), b))
                                .executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "mode")))))
                .then(Commands.literal("inspect")
                        .executes(ctx -> inspect(ctx, true))
                        .then(Commands.argument("eyes", BoolArgumentType.bool())
                                .executes(ctx -> inspect(ctx, BoolArgumentType.getBool(ctx, "eyes")))))
                .then(Commands.literal("clear").executes(BrokenWorldCommand::clear))
                .then(Commands.literal("freeze")
                        .executes(ctx -> freeze(ctx, !SilhouetteEntity.isLookFrozen()))
                        .then(Commands.literal("on").executes(ctx -> freeze(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> freeze(ctx, false))))
                .then(Commands.literal("pose")
                        .then(Commands.argument("pose", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        java.util.List.of("stand", "peek", "crawl", "hang", "spider", "window", "scream", "jerky"), b))
                                .executes(ctx -> pose(ctx, StringArgumentType.getString(ctx, "pose")))))
                .then(Commands.literal("screamer").executes(BrokenWorldCommand::screamer))
                .then(Commands.literal("crash").executes(ctx -> {
                    var r = com.brokenworld.world.FakeCrash.start(ctx.getSource().getPlayerOrException(),
                            ctx.getSource().getLevel().getRandom());
                    // (seen after the "connection" comes back)
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.brokenworld.crash",
                            r.torches(), r.doors(), yesNo(r.chest()), yesNo(r.sign())), false);
                    return 1;
                }))
                .then(Commands.literal("visit").executes(BrokenWorldCommand::visit))
                .then(Commands.literal("faceless")
                        .then(Commands.literal("on").executes(ctx -> faceless(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> faceless(ctx, false))))
                .then(Commands.literal("glitch")
                        .then(Commands.literal("hole").executes(ctx -> glitch(ctx, WorldGlitches.Type.HOLE)))
                        .then(Commands.literal("floating").executes(ctx -> glitch(ctx, WorldGlitches.Type.FLOATING))))
                .then(Commands.literal("playerlike").executes(BrokenWorldCommand::playerLike))
                .then(Commands.literal("sunset").executes(ctx -> {
                    Director.sunsetGlitchNow(ctx.getSource().getServer().overworld());
                    return 1;
                }))
                .then(Commands.literal("chat").executes(ctx -> {
                    int stage = Math.max(1, BrokenWorldState.get(ctx.getSource().getServer()).getStage());
                    com.brokenworld.world.FakeChat.visit(ctx.getSource().getPlayerOrException(), stage);
                    return 1;
                }))
                .then(Commands.literal("finale")
                        .executes(ctx -> setStage(ctx, BrokenWorldState.FINALE_STAGE))
                        .then(Commands.literal("stop").executes(BrokenWorldCommand::stopFinale)))
                .then(Commands.literal("corruption")
                        .then(Commands.literal("reset").executes(ctx -> corruption(ctx, -1F)))
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0F, 1F))
                                .executes(ctx -> corruption(ctx, FloatArgumentType.getFloat(ctx, "value")))))
                .then(Commands.literal("event")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(WorldEvents.Type.values()).map(t -> t.name().toLowerCase(Locale.ROOT)), b))
                                .executes(BrokenWorldCommand::event))));
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        BrokenWorldState state = BrokenWorldState.get(ctx.getSource().getServer());
        long left = Math.max(0, state.getTicksToNextStage() - state.getTicksInStage());
        String next = state.getStage() >= BrokenWorldState.MAX_STAGE ? "-" : String.format(Locale.ROOT, "%.2f", left / 24000.0);
        String corruption = String.format(Locale.ROOT, "%.2f", state.getCorruption());
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.brokenworld.info",
                state.getStage(), next, corruption), false);
        return state.getStage();
    }

    private static int setStage(CommandContext<CommandSourceStack> ctx, int stage) {
        BrokenWorldState.get(ctx.getSource().getServer()).setStage(stage, RandomSource.create());
        if (stage == 0) Director.setFaceless(ctx.getSource().getServer(), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.brokenworld.stage", stage), true);
        return stage;
    }

    /** A silhouette 3 blocks in front of you that never disappears (hit it to remove it). */
    private static int inspect(CommandContext<CommandSourceStack> ctx, boolean eyes) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        BlockPos front = BlockPos.containing(player.getX() + flat.x * 3, player.getY(), player.getZ() + flat.z * 3);
        BlockPos pos = null;
        for (int dy = 0; dy <= 4 && pos == null; dy++) {
            if (SpawnFinder.isStandable(level, front.below(dy), false)) pos = front.below(dy);
            else if (SpawnFinder.isStandable(level, front.above(dy), false)) pos = front.above(dy);
        }
        if (pos == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.brokenworld.no_spot"));
            return 0;
        }
        SilhouetteEntity e = ModEntities.SILHOUETTE.get().create(level);
        if (e == null) return 0;
        e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        e.setup(player, Mode.FAR, eyes);
        e.setStay(eyes);
        level.addFreshEntity(e);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.brokenworld.inspect"), false);
        return 1;
    }

    /** Shows the screamer, then brings the picture back - in the finale, going home does that. */
    private static int screamer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Director.screamer(player);
        return 1;
    }

    private static int faceless(CommandContext<CommandSourceStack> ctx, boolean on) {
        Director.setFaceless(ctx.getSource().getServer(), on);
        return 1;
    }

    private static int glitch(CommandContext<CommandSourceStack> ctx, WorldGlitches.Type type) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int stage = Math.max(2, BrokenWorldState.get(ctx.getSource().getServer()).getStage());
        boolean ok = WorldGlitches.run(type, player.serverLevel(), player, RandomSource.create(), stage);
        if (!ok) ctx.getSource().sendFailure(Component.translatable("commands.brokenworld.event_failed"));
        return ok ? 1 : 0;
    }

    private static int playerLike(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        net.minecraft.world.entity.PathfinderMob mob = PlayerLikeAnimals.nearest(ctx.getSource().getPlayerOrException());
        if (mob == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.brokenworld.no_spot"));
            return 0;
        }
        PlayerLikeAnimals.convert(mob);
        return 1;
    }

    private static int stopFinale(CommandContext<CommandSourceStack> ctx) {
        Finale.stop(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.brokenworld.finale_stopped"), true);
        return 1;
    }

    /** Somebody visits your house right now (around your bed, or around you if you have none): everything at once. */
    private static int visit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos bed = level.dimension() == net.minecraft.world.level.Level.OVERWORLD
                ? com.brokenworld.world.HomeVisit.bed(level, player) : null;
        BlockPos home = bed != null ? bed : player.blockPosition();
        var done = com.brokenworld.world.HomeVisit.visit(level, player, home, level.getRandom(),
                com.brokenworld.world.HomeVisit.Action.values().length);
        net.minecraft.network.chat.MutableComponent message = Component.translatable("commands.brokenworld.visit", done.size());
        for (var a : com.brokenworld.world.HomeVisit.Action.values()) {
            boolean ok = done.contains(a);
            message.append(Component.literal(ok ? "\n \u2714 " : "\n \u2718 ")
                            .withStyle(ok ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GRAY))
                    .append(Component.translatable("commands.brokenworld.visit." + a.name().toLowerCase(Locale.ROOT)));
        }
        ctx.getSource().sendSuccess(() -> message, false);
        return done.size();
    }

    private static Component yesNo(boolean b) {
        return Component.translatable(b ? "gui.yes" : "gui.no");
    }

    /** Silhouettes put up with inspect / pose stop (or go back to) turning after the player. */
    private static int freeze(CommandContext<CommandSourceStack> ctx, boolean frozen) {
        SilhouetteEntity.setLookFrozen(frozen);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                frozen ? "commands.brokenworld.freeze.on" : "commands.brokenworld.freeze.off"), false);
        return 1;
    }

    /** Like inspect, but in one of the Blender poses (or screaming / jerking), to look at it up close. */
    private static int pose(CommandContext<CommandSourceStack> ctx, String pose) throws CommandSyntaxException {
        if (inspect(ctx, true) == 0) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SilhouetteEntity e = player.serverLevel().getEntities(ModEntities.SILHOUETTE.get(),
                        x -> x.distanceToSqr(player) < 36).stream()
                .min(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(player))).orElse(null);
        if (e == null) return 0;
        switch (pose.toLowerCase(Locale.ROOT)) {
            case "peek" -> e.setStance(SilhouetteEntity.Stance.PEEK);
            case "crawl" -> e.setStance(SilhouetteEntity.Stance.CRAWL);
            case "spider" -> e.setStance(SilhouetteEntity.Stance.SPIDER);
            case "window" -> e.setStance(SilhouetteEntity.Stance.WINDOW);
            case "hang" -> {
                e.setStance(SilhouetteEntity.Stance.HANG);
                e.setNoGravity(true);
                e.setPos(e.getX(), e.getY() + 0.45, e.getZ()); // feet up at 3 blocks
            }
            case "scream" -> e.setJawOpen(true);
            case "jerky" -> {
                e.setJerky(true);
                e.setJawOpen(true);
            }
            default -> {
            }
        }
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        List<? extends SilhouetteEntity> all = level.getEntities(ModEntities.SILHOUETTE.get(), e -> true);
        all.forEach(e -> e.vanish(false));
        int count = all.size();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.brokenworld.cleared", count), false);
        return count;
    }

    private static int summon(CommandContext<CommandSourceStack> ctx, String modeName) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        int stage = Math.max(1, BrokenWorldState.get(ctx.getSource().getServer()).getStage());
        Mode mode;
        if (modeName == null) {
            mode = Director.pickMode(level, player, stage);
        } else {
            try {
                mode = Mode.valueOf(modeName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                ctx.getSource().sendFailure(Component.translatable("commands.brokenworld.bad_mode", modeName));
                return 0;
            }
        }
        SilhouetteEntity e = Director.spawn(level, player, mode, stage);
        if (e == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.brokenworld.no_spot"));
            return 0;
        }
        Mode actual = e.getMode();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.brokenworld.summoned",
                actual.name().toLowerCase(Locale.ROOT),
                (int) e.getX(), (int) e.getY(), (int) e.getZ()), false);
        return 1;
    }

    /** Forces how mixed-up the textures are (reset = follow the stage again). */
    private static int corruption(CommandContext<CommandSourceStack> ctx, float value) {
        BrokenWorldState state = BrokenWorldState.get(ctx.getSource().getServer());
        state.setCorruptionOverride(value);
        float now = state.getCorruption();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.brokenworld.corruption",
                String.format(Locale.ROOT, "%.2f", now)), true);
        return Math.round(now * 100);
    }

    private static int event(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "type");
        WorldEvents.Type type;
        try {
            type = WorldEvents.Type.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.translatable("commands.brokenworld.bad_event", name));
            return 0;
        }
        boolean ok = WorldEvents.run(type, player.serverLevel(), player, RandomSource.create());
        if (!ok) ctx.getSource().sendFailure(Component.translatable("commands.brokenworld.event_failed"));
        return ok ? 1 : 0;
    }
}
