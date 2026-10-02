package com.brokenworld;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // Defaults: the world breaks on day 2 and the finale comes on day 3.
    // Days are in-game days: sleeping through the night counts too.

    public static final ForgeConfigSpec.DoubleValue MIN_DAYS_BEFORE_CHANGE = BUILDER
            .comment("Minimum in-game days of normal survival before the world 'breaks' (textures mix up, the silhouette appears)")
            .defineInRange("minDaysBeforeChange", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue MAX_DAYS_BEFORE_CHANGE = BUILDER
            .comment("Maximum in-game days before the world 'breaks'")
            .defineInRange("maxDaysBeforeChange", 1.4, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue DAYS_TO_STAGE_2 = BUILDER
            .comment("In-game days from stage 1 to stage 2 (it gets closer)")
            .defineInRange("daysToStage2", 0.4, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue DAYS_TO_STAGE_3 = BUILDER
            .comment("In-game days from stage 2 to stage 3 (it stops hiding)")
            .defineInRange("daysToStage3", 0.4, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue DAYS_TO_FINALE = BUILDER
            .comment("In-game days from stage 3 to the finale (players fall through the world into the tunnel)")
            .defineInRange("daysToFinale", 0.4, 0.0, 100.0);

    public static final ForgeConfigSpec.BooleanValue FINALE = BUILDER
            .comment("Enable the finale. If false, stage 3 simply goes on forever")
            .define("finale", true);

    public static final ForgeConfigSpec.BooleanValue DELETE_WORLD_AT_END = BUILDER
            .comment("When the monster reaches you at the very end, the game leaves the world and DELETES it (singleplayer only).",
                    "Set to false to only leave the world. On a server players are just disconnected.")
            .define("deleteWorldAtEnd", true);

    public static final ForgeConfigSpec.BooleanValue FAKE_CHAT = BUILDER
            .comment("Once the world is broken: somebody else 'joins' your world now and then and writes in chat")
            .define("fakeChat", true);

    public static final ForgeConfigSpec.BooleanValue BIGGER_HOUSE = BUILDER
            .comment("Once the world is broken: after going far from home, your house is bigger on the inside")
            .define("biggerHouse", true);

    public static final ForgeConfigSpec.DoubleValue APPEARANCE_FREQUENCY = BUILDER
            .comment("Multiplier for how often the silhouette and events happen (2.0 = twice as often)")
            .defineInRange("appearanceFrequency", 1.0, 0.1, 20.0);

    public static final ForgeConfigSpec.BooleanValue JUMPSCARES = BUILDER
            .comment("Allow the rare 'rush' jumpscare in stage 3")
            .define("jumpscares", true);

    public static final ForgeConfigSpec.BooleanValue WORLD_EVENTS = BUILDER
            .comment("Allow world events: torches going out, doors opening, footsteps, mining sounds")
            .define("worldEvents", true);

    public static final ForgeConfigSpec.BooleanValue FAKE_CRASH = BUILDER
            .comment("From stage 2: once per stage the game seems to lose its connection - and comes back with things moved around")
            .define("fakeCrash", true);

    public static final ForgeConfigSpec.BooleanValue HOME_VISITS = BUILDER
            .comment("From stage 2: while you are away, somebody visits your house (a sign, a diary, moved torches, open doors, footprints)")
            .define("homeVisits", true);

    public static final ForgeConfigSpec.BooleanValue BROKEN_SOUNDS = BUILDER
            .comment("Sounds break along with the textures: some sound like other things, stutter, drop out, music slows down")
            .define("brokenSounds", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
