package de.staticfx.heavysuspension;

import de.staticfx.heavysuspension.data.VehicleInput;
import de.staticfx.heavysuspension.data.VehicleLoadDistribution;
import de.staticfx.heavysuspension.data.WheelInput;
import de.staticfx.heavysuspension.data.WheelTuning;

import java.util.Optional;

public class WasmWheelPhysics implements WheelPhysics {

    private final WasmPhysicsBridge bridge;

    public WasmWheelPhysics(WasmPhysicsBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public Optional<WheelTuning> tuneWheel(WheelInput input) {
        return Optional.empty();
    }

    @Override
    public VehicleLoadDistribution calculateVehicleLoadDistribution(VehicleInput input) {
        return null;
    }
}
