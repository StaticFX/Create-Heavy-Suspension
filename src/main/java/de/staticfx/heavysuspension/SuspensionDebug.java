package de.staticfx.heavysuspension;

import com.mojang.logging.LogUtils;
import de.staticfx.heavysuspension.data.WheelTuning;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

public final class SuspensionDebug {
    public static final boolean ENABLED = true;

    private static final Logger LOGGER = LogUtils.getLogger();

    private SuspensionDebug() {
    }

    public static void logWheel(BlockEntity wheel, WheelTuning tuning, double compression,
                                double wheelImpulse, double heaveImpulse,
                                double heaveVelocity, double rotationVelocity) {
        if (!ENABLED || wheel.getLevel() == null)
            return;

        LOGGER.info("[HS] tick={} wheel={} compression={} wheelImpulse={} heaveImpulse={} heaveVel={} rotVel={} spring={} heaveDamp={} rotDamp={}",
                wheel.getLevel().getGameTime(),
                wheel.getBlockPos().toShortString(),
                format(compression),
                format(wheelImpulse),
                format(heaveImpulse),
                format(heaveVelocity),
                format(rotationVelocity),
                tuning == null ? "-" : format(tuning.springiness()),
                tuning == null ? "-" : format(tuning.heaveDamping()),
                tuning == null ? "-" : format(tuning.rotationDamping()));
    }

    private static String format(double value) {
        return String.format("%.3f", value);
    }
}
