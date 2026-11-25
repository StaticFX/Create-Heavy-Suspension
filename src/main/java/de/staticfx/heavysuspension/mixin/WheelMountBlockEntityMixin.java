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
import de.staticfx.heavysuspension.data.WheelInput;
import de.staticfx.heavysuspension.data.WheelTuning;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;


@Pseudo
@Mixin(targets = "dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity", remap = false)
public abstract class WheelMountBlockEntityMixin implements WheelContact {
    @Shadow
    private ForceTotal forceTotal;

    @Unique
    private static final double DAMPING_RATIO = 0.7;

    @Unique
    private WheelTuning heavysuspension$tuning;
    @Unique
    private double heavysuspension$heaveForce;
    @Unique
    private boolean heavysuspension$touching;
    @Unique
    private double heavysuspension$heaveVelocity;
    @Unique
    private double heavysuspension$rotationVelocity;

    @Inject(method = "addBehaviours", at = @At("TAIL"))
    private void heavysuspension$replaceStrengthDial(List<BlockEntityBehaviour> behaviours, CallbackInfo ci) {
        for (BlockEntityBehaviour behaviour : behaviours)
            if (behaviour.getType() == ScrollValueBehaviour.TYPE && behaviour instanceof ScrollValueBehaviour originalDial)
                originalDial.onlyActiveWhen(() -> false);
        behaviours.add(new StiffnessBehaviour((SmartBlockEntity) (Object) this));
    }

    @ModifyVariable(method = "sable$physicsTick", at = @At("STORE"), name = "effectiveStrength")
    private double heavysuspension$tuneToRealMass(double effectiveStrength, @Local(name = "normalMass") double normalMass) {
        return heavysuspension$isUsableMass(normalMass) ? normalMass : effectiveStrength;
    }

    @ModifyVariable(method = "sable$physicsTick", at = @At("STORE"), name = "springStrength")
    private double heavysuspension$tuneSpring(double springStrength,
                                              @Local(name = "normalMass") double normalMass,
                                              @Local(name = "massData") MassData massData,
                                              @Local(argsOnly = true) ServerSubLevel subLevel,
                                              @Local(argsOnly = true) double timeStep) {
        heavysuspension$tuning = heavysuspension$tune(normalMass, massData, subLevel, timeStep);
        heavysuspension$touching = false;

        if (heavysuspension$tuning != null) {
            return heavysuspension$tuning.springiness();
        }

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

    @ModifyVariable(method = "sable$physicsTick", at = @At("STORE"), name = "dampingForce")
    private double heavysuspension$splitDamping(double dampingForce,
                                                @Local(name = "localVelocity") Vector3d localVelocity,
                                                @Local(name = "pose") Pose3d pose,
                                                @Local(argsOnly = true) RigidBodyHandle handle) {
        heavysuspension$heaveForce = 0;
        double heaveVelocity = pose.transformNormalInverse(handle.getLinearVelocity(new Vector3d())).y;
        double rotationVelocity = localVelocity.y - heaveVelocity;
        heavysuspension$heaveVelocity = heaveVelocity;
        heavysuspension$rotationVelocity = rotationVelocity;

        if (heavysuspension$tuning == null) {
            return dampingForce;
        }

        heavysuspension$heaveForce = -heavysuspension$tuning.heaveDamping() * heaveVelocity;

        return -heavysuspension$tuning.rotationDamping() * rotationVelocity;
    }

    @ModifyVariable(method = "sable$physicsTick", at = @At("STORE"), name = "springForce")
    private double heavysuspension$onlyPush(double springForce,
                                            @Local(name = "springLength") double springLength,
                                            @Local(argsOnly = true) double timeStep) {
        if (springLength >= 0.65) {
            return 0;
        }

        heavysuspension$touching = true;
        double push = Math.max(0, springForce);
        double heaveImpulse = 0;

        if (heavysuspension$tuning != null) {
            heaveImpulse = Math.max(heavysuspension$heaveForce * timeStep, -push);
            forceTotal.applyLinearImpulse(new Vector3d(0, heaveImpulse, 0));
        }

        SuspensionDebug.logWheel((BlockEntity) (Object) this, heavysuspension$tuning, 0.65 - springLength,
                push, heaveImpulse, heavysuspension$heaveVelocity, heavysuspension$rotationVelocity);
        return push;
    }

    @Unique
    private WheelTuning heavysuspension$tune(double normalMass, MassData massData, ServerSubLevel subLevel, double timeStep) {
        WheelPhysics physics = PhysicsProvider.physics();
        BlockEntity self = (BlockEntity) (Object) this;
        if (physics == null || self.getLevel() == null)
            return null;

        VehicleLoads loads = VehicleLoads.of(subLevel, self, massData, timeStep, physics);

        WheelInput input = new WheelInput(
                massData.getMass(),
                normalMass,
                loads.loadFraction(self),
                heavysuspension$stiffnessMultiplier(),
                loads.wheelCount(),
                loads.touchingCount(),
                timeStep);

        return physics.tuneWheel(input).orElse(null);
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
    public boolean heavysuspension$isTouching() {
        return heavysuspension$touching;
    }
}
