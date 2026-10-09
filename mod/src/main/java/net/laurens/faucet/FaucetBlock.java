package net.laurens.faucet;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class FaucetBlock extends Block implements SimpleWaterloggedBlock {
	public static final MapCodec<FaucetBlock> CODEC = simpleCodec(FaucetBlock::new);

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	public static final EnumProperty<Liquid> LIQUID = EnumProperty.create("liquid", Liquid.class);

	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.box(5, 5, 5, 11, 15, 16));
	private static final Map<Direction, VoxelShape> SHAPES_CONNECTED = Shapes.rotateHorizontal(Block.box(5, 0, 5, 11, 10, 16));

	public FaucetBlock(Properties properties) {
		super(properties.lightLevel(s -> s.getValue(LIQUID) == Liquid.LAVA ? 7 : 0));
		this.registerDefaultState(this.stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(ENABLED, false)
				.setValue(POWERED, false)
				.setValue(CONNECTED, false)
				.setValue(WATERLOGGED, false)
				.setValue(LIQUID, Liquid.NONE));
	}

	@Override
	protected MapCodec<? extends FaucetBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ENABLED, POWERED, CONNECTED, WATERLOGGED, LIQUID);
	}

	public static boolean isOpen(BlockState state) {
		return state.getValue(POWERED) ^ state.getValue(ENABLED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return (state.getValue(CONNECTED) ? SHAPES_CONNECTED : SHAPES).get(state.getValue(FACING));
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction face = context.getClickedFace();
		Direction facing = face.getAxis() == Direction.Axis.Y ? context.getHorizontalDirection().getOpposite() : face;
		return this.defaultBlockState()
				.setValue(FACING, facing)
				.setValue(CONNECTED, FaucetLogic.canConnectDown(level, pos.below()))
				.setValue(WATERLOGGED, level.getFluidState(pos).is(Fluids.WATER))
				.setValue(POWERED, level.hasNeighborSignal(pos));
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
		scheduleUpdate(level, pos);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		boolean wasOpen = isOpen(state);
		level.playSound(null, pos, Faucet.FAUCET_SOUND, SoundSource.BLOCKS, 1F, wasOpen ? 1F : 1.2F);
		level.gameEvent(player, wasOpen ? GameEvent.BLOCK_DEACTIVATE : GameEvent.BLOCK_ACTIVATE, pos);
		if (!level.isClientSide()) {
			level.setBlock(pos, state.cycle(ENABLED), Block.UPDATE_ALL);
			scheduleUpdate(level, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		boolean powered = level.hasNeighborSignal(pos);
		if (powered != state.getValue(POWERED)) {
			level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
		}
		// Fluid lookups happen in tick(): neighbours can change while a transfer transaction is still closing,
		// and opening another one from here would crash.
		scheduleUpdate(level, pos);
	}

	private void scheduleUpdate(Level level, BlockPos pos) {
		// while open, the transfer loop already has a tick queued and picks up changes on its own
		if (!level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, this)) {
			level.scheduleTick(pos, this, 1);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		state = updateVisuals(state, level, pos);
		if (!isOpen(state)) return;
		// Queue the next step before transferring, so neighbour updates caused by the transfer
		// see a pending tick and don't queue an earlier one that would skip the cooldown.
		FaucetLogic.Transfer transfer = FaucetLogic.planTransfer(level, pos, state);
		level.scheduleTick(pos, this, transfer == null ? FaucetLogic.COOLDOWN_PER_BOTTLE : transfer.cooldown());
		if (transfer == null) return;
		transfer.apply().run();
		BlockState after = level.getBlockState(pos);
		// the transfer may have emptied what's behind us
		if (after.is(this)) updateVisuals(after, level, pos);
	}

	private static BlockState updateVisuals(BlockState state, Level level, BlockPos pos) {
		FaucetLogic.Offer offer = FaucetLogic.findSource(level, pos, state);
		BlockState newState = state
				.setValue(CONNECTED, FaucetLogic.canConnectDown(level, pos.below()))
				.setValue(LIQUID, offer == null ? Liquid.NONE : offer.liquid());
		if (newState != state) level.setBlock(pos, newState, Block.UPDATE_ALL);
		return newState;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
									 Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return state;
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		Liquid liquid = state.getValue(LIQUID);
		if (liquid == Liquid.NONE || state.getValue(CONNECTED)) return;
		boolean open = isOpen(state);
		// closed faucets only drip occasionally
		if (!open && random.nextFloat() > 0.06F) return;
		ParticleOptions particle = switch (liquid) {
			case LAVA -> ParticleTypes.DRIPPING_LAVA;
			case HONEY -> ParticleTypes.DRIPPING_HONEY;
			case POWDER_SNOW -> ParticleTypes.SNOWFLAKE;
			default -> ParticleTypes.DRIPPING_WATER;
		};
		double spread = 0.125;
		double x = pos.getX() + 0.5 + spread * (random.nextFloat() - 0.5);
		double y = open ? pos.getY() - 0.6 : pos.getY() + 0.3;
		double z = pos.getZ() + 0.5 + spread * (random.nextFloat() - 0.5);
		level.addParticle(particle, x, y, z, 0, 0, 0);
	}
}
