package de.staticfx.heavysuspension;

import de.staticfx.heavysuspension.behaviors.StiffnessBehaviour;
import de.staticfx.heavysuspension.data.BodyMotion;
import de.staticfx.heavysuspension.data.MassProperties;
import de.staticfx.heavysuspension.data.SolverWheel;
import de.staticfx.heavysuspension.data.VehicleInput;
import de.staticfx.heavysuspension.data.VehicleLoadDistribution;
import de.staticfx.heavysuspension.data.VehicleSolveInput;
import de.staticfx.heavysuspension.data.Wheel;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class VehicleLoads {
    private static final Map<ServerSubLevel, VehicleLoads> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private final long tick;
    private final List<BlockEntity> wheels;
    private final List<Vec3> contacts;
    private final VehicleLoadDistribution distribution;

    private double[] impulses;
    private boolean[] consumed;

    private VehicleLoads(long tick, List<BlockEntity> wheels, List<Vec3> contacts, VehicleLoadDistribution distribution) {
        this.tick = tick;
        this.wheels = wheels;
        this.contacts = contacts;
        this.distribution = distribution;
    }

    public static VehicleLoads of(ServerSubLevel subLevel, BlockEntity wheel, MassData massData, double timeStep, WheelPhysics physics) {
        long tick = wheel.getLevel().getGameTime();
        VehicleLoads cached = CACHE.get(subLevel);
        if (cached != null && cached.tick == tick) {
            return cached;
        }

        VehicleLoads loads = compute(subLevel, wheel.getClass(), massData, timeStep, physics, tick);
        CACHE.put(subLevel, loads);
        return loads;
    }

    public int wheelCount() {
        return Math.max(1, wheels.size());
    }

    public double loadFraction(BlockEntity wheel) {
        int index = wheels.indexOf(wheel);
        return index < 0 ? 1.0 / wheelCount() : distribution.loadFractions()[index];
    }

    public synchronized double impulseFor(BlockEntity wheel, MassData massData, BodyMotion motion, double timeStep, WheelPhysics physics) {
        int index = wheels.indexOf(wheel);
        if (index < 0) {
            return Double.NaN;
        }

        if (consumed == null || consumed[index]) {
            impulses = physics.solveVehicle(solveInput(index, massData, motion, timeStep)).orElse(null);
            consumed = new boolean[wheels.size()];
        }

        consumed[index] = true;
        return impulses == null ? Double.NaN : impulses[index];
    }

    private VehicleSolveInput solveInput(int freshIndex, MassData massData, BodyMotion motion, double timeStep) {
        List<SolverWheel> solverWheels = new ArrayList<>(wheels.size());
        for (int i = 0; i < wheels.size(); i++) {
            BlockEntity wheel = wheels.get(i);
            double compression = wheel instanceof WheelContact contact ? contact.heavysuspension$compression() : WheelContact.NOT_IN_RANGE;
            solverWheels.add(new SolverWheel(
                    contacts.get(i),
                    StiffnessBehaviour.multiplierOf(wheel),
                    distribution.loadFractions()[i],
                    compression,
                    i == freshIndex ? 0 : 1));
        }

        return new VehicleSolveInput(massProperties(massData), solverWheels, motion, timeStep);
    }

    private static VehicleLoads compute(ServerSubLevel subLevel, Class<?> wheelType, MassData massData, double timeStep, WheelPhysics physics, long tick) {
        List<BlockEntity> wheels = new ArrayList<>();

        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor.getClass() == wheelType) {
                wheels.add((BlockEntity) actor);
            }
        }

        List<Vec3> contacts = wheels.stream().map(VehicleLoads::contactPoint).toList();
        List<Wheel> inputs = new ArrayList<>(wheels.size());
        for (int i = 0; i < wheels.size(); i++) {
            inputs.add(new Wheel(contacts.get(i), StiffnessBehaviour.multiplierOf(wheels.get(i))));
        }

        VehicleInput input = new VehicleInput(massProperties(massData), inputs, timeStep);

        VehicleLoadDistribution distribution = physics.calculateVehicleLoadDistribution(input).orElseGet(() -> equalSplit(wheels.size()));

        return new VehicleLoads(tick, wheels, contacts, distribution);
    }

    private static MassProperties massProperties(MassData massData) {
        Vector3dc centerOfMass = massData.getCenterOfMass();

        return new MassProperties(
                massData.getMass(),
                massData.getInverseMass(),
                new Vec3(centerOfMass.x(), centerOfMass.y(), centerOfMass.z()),
                massData.getInverseInertiaTensor().get(new double[9]));
    }

    private static VehicleLoadDistribution equalSplit(int wheelCount) {
        double[] fractions = new double[wheelCount];
        Arrays.fill(fractions, 1.0 / Math.max(1, wheelCount));

        return new VehicleLoadDistribution(fractions);
    }

    private static Vec3 contactPoint(BlockEntity wheel) {
        BlockPos pos = wheel.getBlockPos();
        return wheel.getBlockState().getOptionalValue(BlockStateProperties.HORIZONTAL_FACING)
                .map(facing -> pos.relative(facing).getCenter())
                .orElse(pos.getCenter());
    }
}
