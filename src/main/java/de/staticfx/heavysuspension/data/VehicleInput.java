package de.staticfx.heavysuspension.data;

import java.util.List;

public record VehicleInput(MassProperties mass, List<Wheel> wheels, double timeStep) {}
