package de.staticfx.heavysuspension;


import de.staticfx.heavysuspension.data.VehicleInput;
import de.staticfx.heavysuspension.data.VehicleLoadDistribution;
import de.staticfx.heavysuspension.data.VehicleSolveInput;
import de.staticfx.heavysuspension.data.WheelInput;
import de.staticfx.heavysuspension.data.WheelTuning;

import java.util.Optional;

public interface WheelPhysics {
    Optional<WheelTuning> tuneWheel(WheelInput input);
    Optional<VehicleLoadDistribution> calculateVehicleLoadDistribution(VehicleInput input);
    Optional<double[]> solveVehicle(VehicleSolveInput input);
}