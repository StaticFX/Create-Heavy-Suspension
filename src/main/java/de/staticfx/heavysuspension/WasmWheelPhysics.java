package de.staticfx.heavysuspension;

import com.dylibso.chicory.runtime.ExportFunction;
import de.staticfx.heavysuspension.data.VehicleInput;
import de.staticfx.heavysuspension.data.VehicleLoadDistribution;
import de.staticfx.heavysuspension.data.WheelInput;
import de.staticfx.heavysuspension.data.WheelTuning;

import java.util.Optional;

public class WasmWheelPhysics implements WheelPhysics {

    private final WasmPhysicsBridge bridge;

    private final ExportFunction wasmTuneWheel;


    private final int tuneWheelOutPtr;

    public WasmWheelPhysics(WasmPhysicsBridge bridge) {
        this.bridge = bridge;
        this.wasmTuneWheel = bridge.getWasmInstance().export("suspension");

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
    public VehicleLoadDistribution calculateVehicleLoadDistribution(VehicleInput input) {
        return null;
    }
}
