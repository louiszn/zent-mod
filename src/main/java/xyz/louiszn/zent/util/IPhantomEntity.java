package xyz.louiszn.zent.util;

import net.minecraft.entity.mob.Monster;
import xyz.louiszn.zent.entity.endPhantom.PhantomMovementType;

public interface IPhantomEntity extends Monster {
    PhantomMovementType zent_mod$getMovementType();
    void zent_mod$setMovementType(PhantomMovementType type);
}
