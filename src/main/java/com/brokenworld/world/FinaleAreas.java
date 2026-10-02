package com.brokenworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Builds the places of the finale in the (empty) tunnel dimension: the tunnel, the maze and the long hall. */
public final class FinaleAreas {
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private FinaleAreas() {}

    private static void load(ServerLevel level, int x0, int z0, int x1, int z1) {
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++)
                level.getChunk(cx, cz);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, FLAGS);
    }

    // ------------------------------------------------------------------ tunnel

    private static final Block[] WRONG_BLOCKS = {Blocks.MOSSY_COBBLESTONE, Blocks.BIRCH_PLANKS, Blocks.NETHERRACK,
            Blocks.OBSIDIAN, Blocks.BOOKSHELF, Blocks.RED_WOOL, Blocks.BONE_BLOCK, Blocks.OAK_LOG, Blocks.GRASS_BLOCK};

    /** One 1-block slice of the tunnel (along +X) with its floor at floorY, 4 blocks of air inside. */
    public static void tunnelSlice(ServerLevel level, int x, int floorY, int centerZ, boolean endWall) {
        load(level, x, centerZ, x, centerZ);
        RandomSource r = RandomSource.create(x * 341873128712L + centerZ * 132897987541L);
        int ceiling = floorY + 5;
        for (int dz = -2; dz <= 2; dz++) {
            for (int y = floorY; y <= ceiling; y++) {
                boolean shell = endWall || dz == -2 || dz == 2 || y == floorY || y == ceiling;
                BlockState state;
                if (shell && y == ceiling && dz == 0 && Math.floorMod(x, 10) == 5) {
                    state = Blocks.GLOWSTONE.defaultBlockState(); // lit, so the black figure stands out
                } else if (shell) {
                    state = tunnelShell(y == floorY, y == ceiling, r);
                } else {
                    state = AIR;
                }
                set(level, x, y, centerZ + dz, state);
            }
        }
    }

    private static BlockState tunnelShell(boolean floor, boolean ceiling, RandomSource r) {
        if (r.nextInt(40) == 0) return WRONG_BLOCKS[r.nextInt(WRONG_BLOCKS.length)].defaultBlockState();
        if (floor) return (r.nextInt(5) == 0 ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.DEEPSLATE_TILES).defaultBlockState();
        if (ceiling) return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        return switch (r.nextInt(6)) {
            case 0 -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            case 1 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            default -> Blocks.STONE_BRICKS.defaultBlockState();
        };
    }

    // ------------------------------------------------------------------ maze

    public static final int MAZE_CELLS = 13;
    private static final int CELL = 4; // 3 blocks of corridor + 1 of wall

    /**
     * @param start  where players arrive (feet)
     * @param notes  where the 5 notes lie (feet), in far-away dead ends
     * @param pit    the pit to throw the notes into (everything below the floor inside it)
     * @param pitTop centre of the pit at floor level
     */
    public record Maze(BlockPos start, List<BlockPos> notes, AABB pit, BlockPos pitTop, BlockPos origin) {
        /** Centre of a random cell (feet level), for glimpses of it at the end of a corridor. */
        public BlockPos cell(int cx, int cz) {
            return origin.offset(cx * CELL + 2, 1, cz * CELL + 2);
        }
    }

    /** A dark maze of 13x13 cells with a room and a pit in the middle. origin = north-west corner at floor level. */
    public static Maze buildMaze(ServerLevel level, BlockPos origin, RandomSource r) {
        int n = MAZE_CELLS;
        boolean[][] east = new boolean[n][n];   // passage from (x,z) to (x+1,z)
        boolean[][] south = new boolean[n][n];  // passage from (x,z) to (x,z+1)
        carve(east, south, r);
        int c = n / 2;
        for (int x = c - 1; x <= c + 1; x++) {   // a small room around the pit
            for (int z = c - 1; z <= c + 1; z++) {
                if (x < c + 1) east[x][z] = true;
                if (z < c + 1) south[x][z] = true;
            }
        }

        int size = n * CELL + 1;
        int x0 = origin.getX(), y0 = origin.getY(), z0 = origin.getZ();
        load(level, x0 - 4, z0 - 4, x0 + size + 4, z0 + size + 4);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int lx = x % CELL, lz = z % CELL, cx = x / CELL, cz = z / CELL;
                boolean wall;
                if (lx == 0 && lz == 0) wall = true;
                else if (lx == 0) wall = cx == 0 || cx >= n || !east[cx - 1][cz];
                else if (lz == 0) wall = cz == 0 || cz >= n || !south[cx][cz - 1];
                else wall = false;
                set(level, x0 + x, y0, z0 + z, (r.nextInt(6) == 0 ? Blocks.MUD_BRICKS : Blocks.DARK_OAK_PLANKS).defaultBlockState());
                for (int y = 1; y <= 4; y++) {
                    BlockState s = wall
                            ? (r.nextInt(7) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS).defaultBlockState()
                            : AIR;
                    set(level, x0 + x, y0 + y, z0 + z, s);
                }
                set(level, x0 + x, y0 + 5, z0 + z, Blocks.BLACK_CONCRETE.defaultBlockState());
                // very dim light here and there so it is not pitch black
                if (!wall && lx == 2 && lz == 2 && (cx + cz) % 3 == 0) {
                    set(level, x0 + x, y0 + 4, z0 + z, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 5));
                }
            }
        }

        // The pit: a 3x3 hole, 5 deep, in the middle of the room.
        int px = x0 + c * CELL + 2, pz = z0 + c * CELL + 2;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean hole = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                for (int y = y0 - 6; y <= y0; y++) {
                    BlockState s = y == y0 - 6 ? Blocks.BLACK_CONCRETE.defaultBlockState()
                            : hole ? AIR : Blocks.BLACKSTONE.defaultBlockState();
                    set(level, px + dx, y, pz + dz, s);
                }
            }
        }
        set(level, px, y0 + 4, pz, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 7));
        AABB pit = new AABB(px - 1, y0 - 6, pz - 1, px + 2, y0 + 0.2, pz + 2);

        // Notes: in dead ends, as far from the start as possible, not next to each other.
        int[][] dist = distances(east, south);
        List<int[]> deadEnds = new ArrayList<>();
        for (int x = 0; x < n; x++)
            for (int z = 0; z < n; z++)
                if (passages(east, south, x, z) == 1 && !(Math.abs(x - c) <= 1 && Math.abs(z - c) <= 1)) deadEnds.add(new int[]{x, z});
        deadEnds.sort(Comparator.comparingInt((int[] a) -> -dist[a[0]][a[1]]));
        List<int[]> chosen = new ArrayList<>();
        for (int[] d : deadEnds) {
            if (chosen.size() == 5) break;
            if (chosen.stream().allMatch(o -> Math.max(Math.abs(o[0] - d[0]), Math.abs(o[1] - d[1])) >= 3)) chosen.add(d);
        }
        for (int[] d : deadEnds) {
            if (chosen.size() == 5) break;
            if (!chosen.contains(d)) chosen.add(d);
        }
        List<BlockPos> notes = new ArrayList<>();
        for (int[] d : chosen) {
            BlockPos p = new BlockPos(x0 + d[0] * CELL + 2, y0 + 1, z0 + d[1] * CELL + 2);
            notes.add(p);
            set(level, p.getX(), y0 + 3, p.getZ(), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 9));
        }
        BlockPos start = new BlockPos(x0 + 2, y0 + 1, z0 + 2);
        return new Maze(start, notes, pit, new BlockPos(px, y0, pz), origin);
    }

    /** Recursive backtracker. */
    private static void carve(boolean[][] east, boolean[][] south, RandomSource r) {
        int n = east.length;
        boolean[][] seen = new boolean[n][n];
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, 0});
        seen[0][0] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            List<int[]> options = new ArrayList<>();
            for (int[] d : dirs) {
                int nx = cur[0] + d[0], nz = cur[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < n && nz < n && !seen[nx][nz]) options.add(new int[]{nx, nz});
            }
            if (options.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] next = options.get(r.nextInt(options.size()));
            if (next[0] != cur[0]) east[Math.min(cur[0], next[0])][cur[1]] = true;
            else south[cur[0]][Math.min(cur[1], next[1])] = true;
            seen[next[0]][next[1]] = true;
            stack.push(next);
        }
    }

    private static int passages(boolean[][] east, boolean[][] south, int x, int z) {
        int n = east.length, count = 0;
        if (x + 1 < n && east[x][z]) count++;
        if (x > 0 && east[x - 1][z]) count++;
        if (z + 1 < n && south[x][z]) count++;
        if (z > 0 && south[x][z - 1]) count++;
        return count;
    }

    private static int[][] distances(boolean[][] east, boolean[][] south) {
        int n = east.length;
        int[][] dist = new int[n][n];
        for (int[] row : dist) java.util.Arrays.fill(row, -1);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        dist[0][0] = 0;
        queue.add(new int[]{0, 0});
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int x = p[0], z = p[1];
            int[][] next = {
                    x + 1 < n && east[x][z] ? new int[]{x + 1, z} : null,
                    x > 0 && east[x - 1][z] ? new int[]{x - 1, z} : null,
                    z + 1 < n && south[x][z] ? new int[]{x, z + 1} : null,
                    z > 0 && south[x][z - 1] ? new int[]{x, z - 1} : null};
            for (int[] q : next) {
                if (q != null && dist[q[0]][q[1]] < 0) {
                    dist[q[0]][q[1]] = dist[x][z] + 1;
                    queue.add(q);
                }
            }
        }
        return dist;
    }

    // ------------------------------------------------------------------ hall

    public static final int HALL_LENGTH = 150;

    /**
     * @param start   where players arrive (feet), at the entrance end
     * @param monster where it stands (feet), right behind the lectern, in the middle of the far end
     * @param lectern the lectern ("pulpit") with an open book it reads
     */
    public record Hall(BlockPos start, BlockPos monster, BlockPos lectern) {}

    /** A very long, tall, dim hall along +X, like a church nave, with a lectern at the far end. */
    public static Hall buildHall(ServerLevel level, BlockPos origin) {
        int x0 = origin.getX(), y0 = origin.getY(), z0 = origin.getZ();
        int halfWidth = 6, height = 9;
        load(level, x0 - 2, z0 - halfWidth - 2, x0 + HALL_LENGTH + 2, z0 + halfWidth + 2);
        for (int x = -1; x <= HALL_LENGTH + 1; x++) {
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                boolean endWall = x == -1 || x == HALL_LENGTH + 1;
                boolean sideWall = Math.abs(dz) == halfWidth;
                boolean pillar = Math.abs(dz) == halfWidth - 1 && Math.floorMod(x, 10) == 0;
                for (int y = 0; y <= height; y++) {
                    BlockState s;
                    if (y == 0) {
                        s = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                    } else if (y == 1 && Math.abs(dz) <= 1 && !endWall) {
                        s = Blocks.RED_CARPET.defaultBlockState(); // a long red runner down the middle
                    } else if (y == height) {
                        s = Blocks.DARK_OAK_PLANKS.defaultBlockState();
                    } else if (endWall || sideWall) {
                        s = Blocks.STONE_BRICKS.defaultBlockState();
                    } else if (pillar) {
                        s = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                    } else {
                        s = AIR;
                    }
                    set(level, x0 + x, y0 + y, z0 + dz, s);
                }
            }
            if (x >= 0 && x <= HALL_LENGTH && Math.floorMod(x, 10) == 5) {
                set(level, x0 + x, y0 + height - 1, z0,
                        Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
        // The lectern faces its reader, who stands behind it (east) looking toward the players.
        BlockPos lectern = new BlockPos(x0 + HALL_LENGTH - 6, y0 + 1, z0);
        BlockState lecternState = Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.EAST);
        level.setBlock(lectern, lecternState, FLAGS);
        net.minecraft.world.item.ItemStack book = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK);
        net.minecraft.nbt.CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", "...");
        tag.putString("author", "?");
        net.minecraft.nbt.ListTag pages = new net.minecraft.nbt.ListTag();
        pages.add(net.minecraft.nbt.StringTag.valueOf("{\"text\":\"\"}"));
        tag.put("pages", pages);
        LecternBlock.tryPlaceBook(null, level, lectern, lecternState, book);
        return new Hall(new BlockPos(x0 + 3, y0 + 1, z0), new BlockPos(x0 + HALL_LENGTH - 5, y0 + 1, z0), lectern);
    }
}
