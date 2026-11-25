package de.staticfx.heavysuspension;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

public final class SuspensionDebug {
    public static final boolean ENABLED = true;

    private static final Logger LOGGER = LogUtils.getLogger();

    private SuspensionDebug() {
    }

    public static void logWheel(BlockEntity wheel, double compression, double impulse, double offroadImpulse) {
        if (!ENABLED || wheel.getLevel() == null) {
            return;
        }

        LOGGER.info("[HS] tick={} wheel={} compression={} impulse={} offroadImpulse={}",
                wheel.getLevel().getGameTime(),
                wheel.getBlockPos().toShortString(),
                format(compression),
                format(impulse),
                format(offroadImpulse));
    }

    private static String format(double value) {
        return String.format("%.3f", value);
    }
}
