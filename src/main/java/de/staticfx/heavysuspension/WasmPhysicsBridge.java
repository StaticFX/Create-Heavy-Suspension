package de.staticfx.heavysuspension;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasm.types.Value;
import com.mojang.logging.LogUtils;
import de.staticfx.heavysuspension.physics.wasm.HsPhysicsModule;
import org.slf4j.Logger;


public class WasmPhysicsBridge {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int ABI_VERSION = 4;

    public static WasmPhysicsBridge INSTACE = load();


    private final Instance wasmInstance;

    private final ExportFunction alloc;
    private final ExportFunction dealloc;
    private final Memory memory;

    private WasmPhysicsBridge(Instance wasmInstance) {
        this.wasmInstance = wasmInstance;
        this.alloc = wasmInstance.export("alloc");
        this.dealloc = wasmInstance.export("dealloc");
        this.memory = wasmInstance.memory();
    }

    private static WasmPhysicsBridge load() {
        try {
            Instance instance = Instance.builder(HsPhysicsModule.load())
                    .withMachineFactory(HsPhysicsModule::create)
                    .build();
            long version = instance.export("abi_version").apply()[0];

            if (version != ABI_VERSION) {
                LOGGER.warn("Physics wasm ABI {} does not expected, {}", version, ABI_VERSION);
                return null;
            }

            LOGGER.info("Hs physics loaded, abi_version = {}", version);
            return new WasmPhysicsBridge(instance);
        } catch (Throwable e) {
            LOGGER.error("Failed to load physics", e);
        }

        return null;
    }

    public Instance getWasmInstance() {
        return wasmInstance;
    }

    synchronized int alloc(int length) {
        return (int) alloc.apply(length)[0];
    }

    synchronized void dealloc(int ptr, int length) {
        dealloc.apply(ptr, length);
    }

    public long f64(double in) {
        return Value.doubleToLong(in);
    }

    public double fromf64(int ptr, int index) {
        return memory.readDouble(ptr + index * Double.BYTES);
    }
}
