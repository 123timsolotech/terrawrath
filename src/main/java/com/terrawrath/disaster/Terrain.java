package com.terrawrath.disaster;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Terrain queries and block classification shared by all disasters.
 */
public final class Terrain {
    private Terrain() {}

    public static boolean loaded(ServerLevel level, int x, int z) {
        return level.isLoaded(new BlockPos(x, level.getSeaLevel(), z));
    }

    /** Y of the top solid (motion-blocking, non-leaf) block. */
    public static int ground(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /** Y of the top non-air block including leaves, plants and fluids. */
    public static int top(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    /** Y of the top block that is solid ground (ignores water, leaves, logs and plants). */
    public static int solidGround(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        while (y > level.getMinY() + 1) {
            p.setY(y);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES) && s.getFluidState().isEmpty() && Terrain.solid(s)) {
                return y;
            }
            y--;
        }
        return y;
    }

    /** Solid ground for heightmaps (vanilla's motion-blocking tag). */
    public static boolean solid(BlockState s) {
        return s.is(BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP);
    }

    public static boolean isWater(BlockState s) {
        return s.getFluidState().is(FluidTags.WATER);
    }

    /** Things wind and water rip away: plants, leaves, logs, soil, glass, wood, wool. Not stone. */
    public static boolean weak(BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() && s.getBlock() == Blocks.WATER) {
            return false;
        }
        if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS) || s.is(BlockTags.WOOL)
            || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.WOODEN_FENCES)
            || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_DOORS)
            || s.is(BlockTags.CROPS) || s.is(BlockTags.REPLACEABLE)) {
            return true;
        }
        float hardness = s.getBlock().defaultDestroyTime();
        return hardness >= 0 && hardness <= 0.81F;
    }

    /** Man-made building blocks that should collapse in an earthquake. */
    public static boolean structural(BlockState s) {
        return s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS)
            || s.is(BlockTags.STAIRS) || s.is(BlockTags.SLABS) || s.is(BlockTags.WALLS) || s.is(BlockTags.FENCES)
            || s.is(BlockTags.WOOL) || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.LOGS)
            || s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.STONE_BRICKS)
            || s.is(Blocks.BRICKS) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.BOOKSHELF) || s.is(Blocks.HAY_BLOCK)
            || s.is(Blocks.SMOOTH_STONE) || s.is(Blocks.CHISELED_STONE_BRICKS) || s.is(Blocks.CRACKED_STONE_BRICKS)
            || s.is(Blocks.SANDSTONE) || s.is(Blocks.CUT_SANDSTONE);
    }

    /** Never touch these. */
    public static boolean protectedBlock(ServerLevel level, BlockPos pos, BlockState s) {
        return s.getDestroySpeed(level, pos) < 0 || s.is(Blocks.BEDROCK) || s.hasBlockEntity() && s.is(Blocks.SPAWNER);
    }

    /** Debris looks better as whole natural blocks: map plants etc to something solid. */
    public static BlockState debrisLook(BlockState s) {
        if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS) || Terrain.solid(s)) {
            return s.hasBlockEntity() ? Blocks.OAK_PLANKS.defaultBlockState() : s;
        }
        if (s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(BlockTags.FLOWERS)) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        return Blocks.DIRT.defaultBlockState();
    }
}
