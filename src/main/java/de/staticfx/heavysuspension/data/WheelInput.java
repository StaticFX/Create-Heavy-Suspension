package de.staticfx.heavysuspension.data;

public record WheelInput(
        double totalMass,
        double normalMass,
        double loadFraction,
        double stiffnessMultiplier,
        double springScale,
        int wheelCount,
        double timeStep) {}
