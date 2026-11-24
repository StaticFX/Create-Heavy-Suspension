package de.staticfx.heavysuspension;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.dylibso.chicory.runtime.Instance;

import java.io.InputStream;

public class PhysicsBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void readAbi() {
        try (InputStream wasm = PhysicsBridge.class.getResourceAsStream("/hs_physics.wasm")) {
            if (wasm == null) {
                LOGGER.error("Cant load wasm");
                return;
            }

            Instance instance = Instance.builder(Parser.parse(wasm)).build();
            long version = instance.export("abi_version").apply()[0];
            LOGGER.info("Hs physics loaded, abi_version = {}", version);
        } catch (Exception e) {
            LOGGER.error("Failed to load physics", e);
        }
    }
}
