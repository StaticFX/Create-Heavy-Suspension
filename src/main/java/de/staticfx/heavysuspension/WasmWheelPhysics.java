package de.staticfx.heavysuspension;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.wasm.types.Value;
import de.staticfx.heavysuspension.data.*;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Optional;

public class WasmWheelPhysics implements WheelPhysics {

    private final WasmPhysicsBridge bridge;

    private final ExportFunction wasmTuneWheel;
    private final ExportFunction wasmCalcDistribution;


    private final int tuneWheelOutPtr;

    public WasmWheelPhysics(WasmPhysicsBridge bridge) {
        this.bridge = bridge;
        this.wasmTuneWheel = bridge.getWasmInstance().export("suspension");
        this.wasmCalcDistribution = bridge.getWasmInstance().export("calc_distribution");

        tuneWheelOutPtr = bridge.alloc(3 * Double.BYTES);
    }

    @Override
    public Optional<WheelTuning> tuneWheel(WheelInput input) {
        long ok = wasmTuneWheel.apply(
                bridge.f64(input.totalMass()),
                bridge.f64(input.normalMass()),
                bridge.f64(input.loadFraction()),
                bridge.f64(input.stiffnessMultiplier()),
                bridge.f64(input.springScale()),
                input.wheelCount(),
                bridge.f64(input.timeStep()),
                tuneWheelOutPtr)[0];

        if (ok == 0)
            return Optional.empty();

        return Optional.of(new WheelTuning(
                bridge.fromf64(tuneWheelOutPtr, 0),
                bridge.fromf64(tuneWheelOutPtr, 1),
                bridge.fromf64(tuneWheelOutPtr, 2)));
    }



    @Override
    public Optional<VehicleLoadDistribution> calculateVehicleLoadDistribution(VehicleInput input) {
        var allocSize = input.wheels().size() * Double.BYTES;
        int returnPointer = bridge.alloc(allocSize);

        int wheelCount = input.wheels().size();
        int massPtr = writeMassToMemory(input.mass());
        int wheelPtr = writeWheelsToMemory(input.wheels());

        long springScaleLong = wasmCalcDistribution.apply(
                massPtr,
                wheelPtr,
                wheelCount,
                bridge.f64(input.timeStep()),
                returnPointer
        )[0];


        try {
            double springScale = Value.longToDouble(springScaleLong);
            if (Double.isNaN(springScale))
                return Optional.empty();

            double[] fractions = new double[wheelCount];
            for (int i = 0; i < wheelCount; i++)
                fractions[i] = bridge.fromf64(returnPointer, i);

            return Optional.of(new VehicleLoadDistribution(fractions, springScale));
        } finally {
            bridge.dealloc(massPtr, MASS_BYTES);
            bridge.dealloc(wheelPtr, wheelBytes(wheelCount));
            bridge.dealloc(returnPointer, allocSize);
        }
    }

    private static final int MASS_VALUES = 14;
    private static final int MASS_BYTES = MASS_VALUES * Double.BYTES;

    private int writeMassToMemory(MassProperties massData) {
        double[] values = new double[MASS_VALUES];
        Vec3 centerOfMass = massData.centerOfMass();

        values[0] = massData.totalMass();
        values[1] = massData.inverseMass();
        values[2] = centerOfMass.x();
        values[3] = centerOfMass.y();
        values[4] = centerOfMass.z();
        System.arraycopy(massData.inverseInertia(), 0, values, 5, 9);

        int ptr = bridge.alloc(MASS_BYTES);
        for (int i = 0; i < MASS_VALUES; i++) {
            bridge.getWasmInstance().memory().writeF64(ptr + i * Double.BYTES, values[i]);
        }

        return ptr;
    }

    private static final int VALUES_PER_WHEEL = 4;

    private static int wheelBytes(int wheelCount) {
        return wheelCount * VALUES_PER_WHEEL * Double.BYTES;
    }

    private int writeWheelsToMemory(List<Wheel> wheels) {
        double[] values = new double[wheels.size() * VALUES_PER_WHEEL];
        for (int i = 0; i < wheels.size(); i++) {
            Wheel wheel = wheels.get(i);
            int offset = i * VALUES_PER_WHEEL;
            values[offset] = wheel.contact().x();
            values[offset + 1] = wheel.contact().y();
            values[offset + 2] = wheel.contact().z();
            values[offset + 3] = wheel.stiffnessMultiplier();
        }

        int ptr = bridge.alloc(wheelBytes(wheels.size()));
        for (int i = 0; i < values.length; i++) {
            bridge.getWasmInstance().memory().writeF64(ptr + i * Double.BYTES, values[i]);
        }

        return ptr;
    }

}
