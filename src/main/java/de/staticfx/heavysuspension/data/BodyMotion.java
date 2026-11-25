package de.staticfx.heavysuspension.data;

import net.minecraft.world.phys.Vec3;

public record BodyMotion(Vec3 linearVelocity, Vec3 angularVelocity, Vec3 gravity) {}
