package com.terrawrath.disaster;

import java.util.ArrayDeque;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block edits spread across ticks so even huge terrain changes never freeze the game.
 */
public final class BlockQueue {
    /** Fast: clients updated, no neighbour/shape updates (fluids stay where they are). */
    public static final int FAST = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Full physics: neighbours updated, fluids flow, sand falls. */
    public static final int PHYSICS = Block.UPDATE_ALL;

    private sealed interface Op permits SetOp, RunOp {}

    private record SetOp(long pos, BlockState state, int flags) implements Op {}

    private record RunOp(Consumer<ServerLevel> action) implements Op {}

    private final ArrayDeque<Op> ops = new ArrayDeque<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public void set(BlockPos pos, BlockState state) {
        ops.add(new SetOp(pos.asLong(), state, FAST));
    }

    public void set(BlockPos pos, BlockState state, int flags) {
        ops.add(new SetOp(pos.asLong(), state, flags));
    }

    public void set(int x, int y, int z, BlockState state, int flags) {
        ops.add(new SetOp(BlockPos.asLong(x, y, z), state, flags));
    }

    public void run(Consumer<ServerLevel> action) {
        ops.add(new RunOp(action));
    }

    public int size() {
        return ops.size();
    }

    public boolean isEmpty() {
        return ops.isEmpty();
    }

    public void clear() {
        ops.clear();
    }

    /** Applies up to {@code budget} operations; returns how many were used. */
    public int process(ServerLevel level, int budget) {
        int used = 0;
        while (used < budget && !ops.isEmpty()) {
            Op op = ops.poll();
            used++;
            if (op instanceof SetOp s) {
                cursor.set(s.pos);
                if (!level.isLoaded(cursor) || level.isOutsideBuildHeight(cursor)) {
                    continue;
                }
                BlockState current = level.getBlockState(cursor);
                if (current == s.state || current.getDestroySpeed(level, cursor) < 0) {
                    continue; // unchanged or unbreakable (bedrock, portals...)
                }
                level.setBlock(cursor, s.state, s.flags);
            } else if (op instanceof RunOp r) {
                r.action.accept(level);
            }
        }
        return used;
    }
}
