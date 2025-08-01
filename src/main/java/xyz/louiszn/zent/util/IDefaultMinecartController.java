package xyz.louiszn.zent.util;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public interface IDefaultMinecartController {
    void zent_mod$adjustToRail(BlockPos pos, BlockState blockState, boolean ignoreWeight);
}
