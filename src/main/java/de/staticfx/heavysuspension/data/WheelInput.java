package de.staticfx.heavysuspension.data;

public record WheelInput(
        double totalMass,
        double normalMass,
        double loadFraction,
        double stiffnessMultiplier,
        int wheelCount,
        int touchingCount,
        double timeStep) {}
