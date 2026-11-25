package de.staticfx.heavysuspension;

import de.staticfx.heavysuspension.data.WheelTuning;

public interface WheelPhysics {

    WheelTuning calculateTuningForWheel(double normalMass, double loadFraction, double stiffnessMultiplier, double spring, int wheels, double dt);

}
