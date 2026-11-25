package de.staticfx.heavysuspension.data;

import java.util.List;

public record VehicleSolveInput(MassProperties mass, List<SolverWheel> wheels, BodyMotion motion, double timeStep) {}
