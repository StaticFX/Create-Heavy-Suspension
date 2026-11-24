package de.staticfx.heavysuspension;

import net.neoforged.fml.common.Mod;

@Mod(HeavySuspension.MOD_ID)
public class HeavySuspension {
    public static final String MOD_ID = "cheavysuspension";
    private static final String WHEEL_MOUNT = "dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity";

    public HeavySuspension() throws ClassNotFoundException {
        Class.forName(WHEEL_MOUNT, false, HeavySuspension.class.getClassLoader());
    }
}
