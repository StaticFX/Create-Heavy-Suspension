package de.staticfx.heavysuspension;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.Value;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WasmPhysicsBridge {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int ABI_VERSION = 1;

    public static WasmPhysicsBridge INSTACE = load();


    private final Instance wasmInstance;

    private final ExportFunction alloc;
    private final ExportFunction dealloc;
    private final ExportFunction sumI32;
    private final Memory memory;

    private WasmPhysicsBridge(Instance wasmInstance) {
        this.wasmInstance = wasmInstance;
        this.alloc = wasmInstance.export("alloc");
        this.dealloc = wasmInstance.export("dealloc");
        this.sumI32 = wasmInstance.export("sum_i32");
        this.memory = wasmInstance.memory();
    }

    private static WasmPhysicsBridge load() {
        try (InputStream wasm = WasmPhysicsBridge.class.getResourceAsStream("/hs_physics.wasm")) {
            if (wasm == null) {
                LOGGER.error("Cant load wasm");
                return null;
            }

            Instance instance = Instance.builder(Parser.parse(wasm)).build();
            long version = instance.export("abi_version").apply()[0];

            if (version != ABI_VERSION) {
                LOGGER.warn("Physics wasm ABI {} does not expected, {}", version, ABI_VERSION);
                return null;
            }

            LOGGER.info("Hs physics loaded, abi_version = {}", version);
            return new WasmPhysicsBridge(instance);
        } catch (Exception e) {
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

    synchronized byte[] bytesFromIntArray(int[] input) {
        ByteBuffer bb = ByteBuffer.allocate(input.length * Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        bb.asIntBuffer().put(input);
        return bb.array();
    }

    public synchronized void sum(int[] input) {
        ByteBuffer bb = ByteBuffer.allocate(input.length * Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);

        bb.asIntBuffer().put(input);
        byte[] bytes = bb.array();

        int ptr = (int) alloc.apply(bytes.length)[0];
        try {
            memory.write(ptr, bytes);
            long sum = sumI32.apply(ptr, input.length)[0];
            LOGGER.info("Sum is {}", sum);
        } finally {
            dealloc.apply(ptr, bytes.length);
        }
    }

    public long f64(double in) {
        return Value.doubleToLong(in);
    }

    public double fromf64(int ptr, int index) {
        return memory.readDouble(ptr + index * Double.BYTES);
    }

}
