package de.staticfx.heavysuspension.data;

import net.minecraft.world.phys.Vec3;

public record Wheel(Vec3 contact, double stiffnessMultiplier) {}
