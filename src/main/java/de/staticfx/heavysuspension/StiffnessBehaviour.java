package de.staticfx.heavysuspension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.math.BigDecimal;
import java.util.List;


public class StiffnessBehaviour extends ScrollValueBehaviour {
    public static final BehaviourType<StiffnessBehaviour> TYPE = new BehaviourType<>();
    private static final String NBT_KEY = "HeavySuspensionStiffnessPercent";
    private static final String LEGACY_NBT_KEY = "HeavySuspensionStiffness";
    private static final int MIN_PERCENT = 25;
    private static final int MAX_PERCENT = 100_000;
    private static final int BOARD_SIZE = 100;

    public StiffnessBehaviour(SmartBlockEntity be) {
        super(Component.translatable("cheavysuspension.scroll_option.stiffness"), be, new DialSlot());
        between(MIN_PERCENT, MAX_PERCENT);
        withFormatter(StiffnessBehaviour::formatMultiplier);
        value = 100;
    }

    public double multiplier() {
        return value / 100.0;
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        List<Component> rows = List.of(
                Component.translatable("cheavysuspension.scroll_option.stiffness.fine"),
                Component.translatable("cheavysuspension.scroll_option.stiffness.coarse"),
                Component.translatable("cheavysuspension.scroll_option.stiffness.supercoarse")
        );
        return new ValueSettingsBoard(label, BOARD_SIZE, 4, rows, new ValueSettingsFormatter(this::formatBoardEntry));
    }

    @Override
    public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
        if (settings.equals(getValueSettings()))
            return;
        setValue(toPercent(settings));
        playFeedbackSound(this);
    }

    @Override
    public ValueSettings getValueSettings() {
        int row = value <= BOARD_SIZE * step(0) ? 0 : value <= BOARD_SIZE * step(1) ? 1 : 2;
        return new ValueSettings(row, Math.max(1, Math.round(value / (float) step(row))));
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        nbt.putInt(NBT_KEY, value);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        int percent = 100;
        if (nbt.contains(NBT_KEY))
            percent = nbt.getInt(NBT_KEY);
        else if (nbt.contains(LEGACY_NBT_KEY))
            percent = nbt.getInt(LEGACY_NBT_KEY) * 25;
        value = Math.clamp(percent, MIN_PERCENT, MAX_PERCENT);
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public int netId() {
        return 21;
    }

    private MutableComponent formatBoardEntry(ValueSettings settings) {
        return Component.literal(formatMultiplier(toPercent(settings)));
    }

    private static int step(int row) {
        return switch (row) {
            case 0 -> 25;
            case 1 -> 100;
            default -> 1000;
        };
    }

    private static int toPercent(ValueSettings settings) {
        return Math.clamp((long) settings.value() * step(settings.row()), MIN_PERCENT, MAX_PERCENT);
    }

    private static String formatMultiplier(int percent) {
        return BigDecimal.valueOf(percent, 2).stripTrailingZeros().toPlainString() + "×";
    }

    private static class DialSlot extends ValueBoxTransform {
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return VecHelper.rotateCentered(VecHelper.voxelSpace(8, 15.5, 11), angle(state), Direction.Axis.Y);
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            TransformStack.of(ms).rotateYDegrees(angle(state)).rotateXDegrees(90);
        }

        @Override
        public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
            return localHit.distanceTo(getLocalOffset(level, pos, state)) < scale / 3;
        }

        private static float angle(BlockState state) {
            return AngleHelper.horizontalAngle(state.getValue(BlockStateProperties.HORIZONTAL_FACING)) + 180;
        }
    }
}
