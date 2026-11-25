package de.staticfx.heavysuspension.data;

import net.minecraft.world.phys.Vec3;

public record MassProperties(double totalMass, double inverseMass, Vec3 centerOfMass, double[] inverseInertia) {}
