package de.staticfx.heavysuspension;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class PhysicsProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static WheelPhysics physics;

    public static void loadProvider() {
        WasmPhysicsBridge bridge = WasmPhysicsBridge.INSTACE;

        if (bridge != null) {
            physics = new WasmWheelPhysics(bridge);
            LOGGER.info("Loaded WASM-Rust physics");
        } else {
            LOGGER.info("Not able to load WASM-Rust, falling back to java physics");
            // TODO Java physics
        }
    }

    public static WheelPhysics physics() {
        if (physics == null) {
            loadProvider();
        }

        return physics;
    }
}
