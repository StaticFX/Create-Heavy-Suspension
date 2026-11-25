package de.staticfx.heavysuspension;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class SuspensionDebug {
    public static final boolean ENABLED = true;

    private static final Logger LOGGER = LogUtils.getLogger();

    private SuspensionDebug() {
    }

    public static void logWheel(BlockEntity wheel, double compression, double impulse, double offroadImpulse, Vec3i hitNormal, Vec3 offroadDirection) {
        if (!ENABLED || wheel.getLevel() == null) {
            return;
        }

        LOGGER.info("[HS] tick={} wheel={} compression={} impulse={} offroadImpulse={} hitNormal={} offroadForce={}",
                wheel.getLevel().getGameTime(),
                wheel.getBlockPos().toShortString(),
                format(compression),
                format(impulse),
                format(offroadImpulse),
                hitNormal.toShortString(),
                String.format("%.2f, %.2f, %.2f", offroadDirection.x, offroadDirection.y, offroadDirection.z));
    }

    private static String format(double value) {
        return String.format("%.3f", value);
    }
}
