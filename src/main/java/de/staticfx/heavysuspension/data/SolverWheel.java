package de.staticfx.heavysuspension.data;

import net.minecraft.world.phys.Vec3;

public record SolverWheel(Vec3 contact, double stiffnessMultiplier, double loadFraction, double compression, double ageInSteps) {}
