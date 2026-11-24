package de.staticfx.heavysuspension.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import de.staticfx.heavysuspension.StiffnessBehaviour;
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
public abstract class WheelMountBlockEntityMixin {
    @Inject(method = "addBehaviours", at = @At("TAIL"))
    private void heavysuspension$replaceStrengthDial(List<BlockEntityBehaviour> behaviours, CallbackInfo ci) {
        for (BlockEntityBehaviour behaviour : behaviours)
            if (behaviour.getType() == ScrollValueBehaviour.TYPE && behaviour instanceof ScrollValueBehaviour originalDial)
                originalDial.onlyActiveWhen(() -> false);
        behaviours.add(new StiffnessBehaviour((SmartBlockEntity) (Object) this));
    }

    @ModifyVariable(method = "sable$physicsTick", at = @At("STORE"), name = "effectiveStrength")
    private double heavysuspension$tuneToRealMass(double effectiveStrength, @Local(name = "normalMass") double normalMass) {
        return Double.isFinite(normalMass) && normalMass > 0 ? normalMass : effectiveStrength;
    }

    @ModifyVariable(method = "sable$physicsTick", at = @At("STORE"), name = "springStrength")
    private double heavysuspension$stiffenSpring(double springStrength) {
        return springStrength * heavysuspension$stiffnessMultiplier();
    }

    @ModifyVariable(method = "sable$physicsTick", at = @At("STORE"), name = "dampingStrength")
    private double heavysuspension$keepDampingRatio(double dampingStrength) {
        return dampingStrength * Math.sqrt(heavysuspension$stiffnessMultiplier());
    }

    @Unique
    private double heavysuspension$stiffnessMultiplier() {
        StiffnessBehaviour stiffness = ((SmartBlockEntity) (Object) this).getBehaviour(StiffnessBehaviour.TYPE);
        return stiffness == null ? 1 : stiffness.multiplier();
    }
}
