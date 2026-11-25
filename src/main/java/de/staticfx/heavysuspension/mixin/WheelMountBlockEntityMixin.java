package de.staticfx.heavysuspension.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import de.staticfx.heavysuspension.PhysicsProvider;
import de.staticfx.heavysuspension.SuspensionDebug;
import de.staticfx.heavysuspension.VehicleLoads;
import de.staticfx.heavysuspension.WheelContact;
import de.staticfx.heavysuspension.WheelPhysics;
import de.staticfx.heavysuspension.behaviors.StiffnessBehaviour;
import de.staticfx.heavysuspension.data.BodyMotion;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;


@Pseudo
@Mixin(targets = "dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity", remap = false)
public abstract class WheelMountBlockEntityMixin implements WheelContact {
    @Unique
    private static final double DAMPING_RATIO = 0.7;
    @Unique
    private static final double REST_LENGTH = 0.65;

    @Unique
    private double heavysuspension$compression = NOT_IN_RANGE;

    @Inject(method = "addBehaviours", at = @At("TAIL"))
    private void heavysuspension$replaceStrengthDial(List<BlockEntityBehaviour> behaviours, CallbackInfo ci) {
        for (BlockEntityBehaviour behaviour : behaviours) {
            if (behaviour.getType() == ScrollValueBehaviour.TYPE && behaviour instanceof ScrollValueBehaviour originalDial) {
                originalDial.onlyActiveWhen(() -> false);
            }
        }

        behaviours.add(new StiffnessBehaviour((SmartBlockEntity) (Object) this));
    }

    @ModifyVariable(method = "sable$physicsTick", at = @At("STORE"), name = "effectiveStrength")
    private double heavysuspension$tuneToRealMass(double effectiveStrength, @Local(name = "normalMass") double normalMass) {
        return heavysuspension$isUsableMass(normalMass) ? normalMass : effectiveStrength;
    }

    @ModifyVariable(method = "sable$physicsTick", at = @At("STORE"), name = "springStrength")
    private double heavysuspension$stiffenSpring(double springStrength,
                                                 @Local(name = "normalMass") double normalMass,
                                                 @Local(argsOnly = true) double timeStep) {
        heavysuspension$compression = NOT_IN_RANGE;

        double stiffened = springStrength * heavysuspension$stiffnessMultiplier();
        if (!heavysuspension$isUsableMass(normalMass)) {
            return stiffened;
        }

        return Math.min(stiffened, normalMass / (timeStep * timeStep));
    }

    @ModifyVariable(method = "sable$physicsTick", at = @At("STORE"), name = "dampingStrength")
    private double heavysuspension$applyDampingRatio(double dampingStrength, @Local(name = "springStrength") double springStrength, @Local(name = "normalMass") double normalMass) {
        if (!heavysuspension$isUsableMass(normalMass)) {
            return dampingStrength * Math.sqrt(heavysuspension$stiffnessMultiplier());
        }

        return 2 * DAMPING_RATIO * Math.sqrt(springStrength * normalMass);
    }

    @ModifyVariable(method = "sable$physicsTick", at = @At("STORE"), name = "springForce")
    private double heavysuspension$solveSuspension(double springForce,
                                                   @Local(name = "springLength") double springLength,
                                                   @Local(name = "distance") double distance,
                                                   @Local(name = "radius") float radius,
                                                   @Local(name = "massData") MassData massData,
                                                   @Local(name = "pose") Pose3d pose,
                                                   @Local(argsOnly = true) ServerSubLevel subLevel,
                                                   @Local(argsOnly = true) RigidBodyHandle handle,
                                                   @Local(argsOnly = true) double timeStep) {
        BlockEntity self = (BlockEntity) (Object) this;
        heavysuspension$compression = REST_LENGTH - (distance - radius);

        double fallback = springLength >= REST_LENGTH ? 0 : Math.max(0, springForce);
        WheelPhysics physics = PhysicsProvider.physics();
        if (physics == null || self.getLevel() == null) {
            return fallback;
        }

        VehicleLoads loads = VehicleLoads.of(subLevel, self, massData, timeStep, physics);
        double impulse = loads.impulseFor(self, massData, heavysuspension$motion(pose, handle, self), timeStep, physics);
        if (!Double.isFinite(impulse)) {
            return fallback;
        }

        SuspensionDebug.logWheel(self, heavysuspension$compression, impulse, fallback);
        return impulse;
    }

    @Unique
    private static BodyMotion heavysuspension$motion(Pose3d pose, RigidBodyHandle handle, BlockEntity self) {
        Vector3d linear = pose.transformNormalInverse(handle.getLinearVelocity(new Vector3d()));
        Vector3d angular = pose.transformNormalInverse(handle.getAngularVelocity(new Vector3d()));
        Vector3d gravity = pose.transformNormalInverse(DimensionPhysicsData.getGravity(self.getLevel(), pose.position()));
        return new BodyMotion(heavysuspension$vec(linear), heavysuspension$vec(angular), heavysuspension$vec(gravity));
    }

    @Unique
    private static Vec3 heavysuspension$vec(Vector3d vector) {
        return new Vec3(vector.x, vector.y, vector.z);
    }

    @Unique
    private static boolean heavysuspension$isUsableMass(double mass) {
        return Double.isFinite(mass) && mass > 0;
    }

    @Unique
    private double heavysuspension$stiffnessMultiplier() {
        StiffnessBehaviour stiffness = ((SmartBlockEntity) (Object) this).getBehaviour(StiffnessBehaviour.TYPE);
        return stiffness == null ? 1 : stiffness.multiplier();
    }

    @Override
    public double heavysuspension$compression() {
        return heavysuspension$compression;
    }
}
