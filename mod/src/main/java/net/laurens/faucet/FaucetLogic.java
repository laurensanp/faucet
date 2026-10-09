package net.laurens.faucet;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.base.ResourceAmount;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Moves liquid from the block behind the faucet into the block below it, one bottle (or bucket) at a time.
 */
public final class FaucetLogic {
	public static final int COOLDOWN_PER_BOTTLE = 20;
	private static final long BOTTLE = FluidConstants.BOTTLE;
	private static final long BUCKET = FluidConstants.BUCKET;
	private static final long[] TRANSFER_SIZES = {BOTTLE, BUCKET};

	private FaucetLogic() {
	}

	/** Takes up to {@code amount} from a source. Returns how much was (or would be) taken. */
	@FunctionalInterface
	interface Drain {
		long take(long amount, boolean simulate);
	}

	/** A liquid that can be pulled out of the block behind the faucet. {@code fluid} is null for honey and powder snow. */
	record Offer(Liquid liquid, @Nullable FluidVariant fluid, Drain drain) {
	}

	// ---------------- sources (block behind) ----------------

	static @Nullable Offer findSource(Level level, BlockPos faucetPos, BlockState faucetState) {
		if (faucetState.getBlock() instanceof InfiniteWaterFaucetBlock) {
			return new Offer(Liquid.WATER, FluidVariant.of(Fluids.WATER), (amount, simulate) -> amount);
		}
		Direction facing = faucetState.getValue(FaucetBlock.FACING);
		BlockPos behind = faucetPos.relative(facing.getOpposite());
		BlockState state = level.getBlockState(behind);
		if (state.isAir()) return null;

		if (state.hasProperty(BlockStateProperties.LEVEL_HONEY)) {
			if (state.getValue(BlockStateProperties.LEVEL_HONEY) < 5) return null;
			return new Offer(Liquid.HONEY, null, (amount, simulate) -> {
				if (amount != BOTTLE) return 0;
				if (!simulate) level.setBlockAndUpdate(behind, state.setValue(BlockStateProperties.LEVEL_HONEY, 0));
				return BOTTLE;
			});
		}

		if (state.is(Blocks.POWDER_SNOW_CAULDRON)) {
			return new Offer(Liquid.POWDER_SNOW, null, (amount, simulate) -> {
				if (amount != BOTTLE) return 0;
				if (!simulate) lowerCauldron(level, behind, state);
				return BOTTLE;
			});
		}

		// Cauldrons with water/lava and any modded tank exposing Fabric's fluid API.
		Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, behind, facing);
		if (storage != null) {
			FluidVariant fluid = StorageUtil.findExtractableResource(storage, null);
			if (fluid == null) return null;
			return new Offer(liquidOf(fluid), fluid, (amount, simulate) -> {
				try (Transaction tx = Transaction.openOuter()) {
					long taken = storage.extract(fluid, amount, tx);
					if (!simulate && taken == amount) tx.commit();
					return taken;
				}
			});
		}

		FluidState fluidState = level.getFluidState(behind);
		if (fluidState.isSource()) {
			FluidVariant fluid = FluidVariant.of(fluidState.getType());
			if (fluidState.is(Fluids.WATER)) {
				// water source blocks are infinite, like in the original
				return new Offer(Liquid.WATER, fluid, (amount, simulate) -> amount);
			}
			if (state.getBlock() instanceof BucketPickup pickup) {
				return new Offer(liquidOf(fluid), fluid, (amount, simulate) -> {
					if (amount != BUCKET) return 0;
					if (!simulate) pickup.pickupBlock(null, level, behind, state);
					return BUCKET;
				});
			}
		}
		return null;
	}

	private static void lowerCauldron(Level level, BlockPos pos, BlockState state) {
		int newLevel = state.getValue(LayeredCauldronBlock.LEVEL) - 1;
		level.setBlockAndUpdate(pos, newLevel <= 0 ? Blocks.CAULDRON.defaultBlockState()
				: state.setValue(LayeredCauldronBlock.LEVEL, newLevel));
	}

	static Liquid liquidOf(FluidVariant fluid) {
		if (fluid.isOf(Fluids.WATER) || fluid.isOf(Fluids.FLOWING_WATER)) return Liquid.WATER;
		if (fluid.isOf(Fluids.LAVA) || fluid.isOf(Fluids.FLOWING_LAVA)) return Liquid.LAVA;
		return Liquid.OTHER;
	}

	// ---------------- targets (block below) ----------------

	/**
	 * @return amount accepted, or -1 if the block below doesn't care about this liquid at all
	 */
	private static long fill(ServerLevel level, BlockPos below, Offer offer, long amount, boolean simulate) {
		BlockState state = level.getBlockState(below);

		if (offer.liquid() == Liquid.HONEY) {
			if (!state.hasProperty(BlockStateProperties.LEVEL_HONEY)) return -1;
			if (amount != BOTTLE || state.getValue(BlockStateProperties.LEVEL_HONEY) != 0) return 0;
			if (!simulate) level.setBlockAndUpdate(below, state.setValue(BlockStateProperties.LEVEL_HONEY, 5));
			return BOTTLE;
		}

		if (offer.liquid() == Liquid.POWDER_SNOW) {
			if (amount != BOTTLE) return 0;
			if (state.is(Blocks.CAULDRON)) {
				if (!simulate) level.setBlockAndUpdate(below, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState());
				return BOTTLE;
			}
			if (state.is(Blocks.POWDER_SNOW_CAULDRON)) {
				int current = state.getValue(LayeredCauldronBlock.LEVEL);
				if (current >= 3) return 0;
				if (!simulate) level.setBlockAndUpdate(below, state.setValue(LayeredCauldronBlock.LEVEL, current + 1));
				return BOTTLE;
			}
			return -1;
		}

		if (offer.liquid() == Liquid.WATER && amount == BOTTLE) {
			BlockState converted = waterConversion(state);
			if (converted != null) {
				if (!simulate) level.setBlockAndUpdate(below, converted);
				return BOTTLE;
			}
		}

		if (offer.fluid() != null) {
			Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, below, Direction.UP);
			if (storage != null) {
				try (Transaction tx = Transaction.openOuter()) {
					long inserted = storage.insert(offer.fluid(), amount, tx);
					if (!simulate && inserted == amount) tx.commit();
					return inserted;
				}
			}
		}
		return -1;
	}

	private static @Nullable BlockState waterConversion(BlockState state) {
		if (state.is(Blocks.DIRT)) return Blocks.MUD.defaultBlockState();
		if (state.getBlock() instanceof ConcretePowderBlock) {
			for (DyeColor color : DyeColor.values()) {
				if (Blocks.CONCRETE_POWDER.pick(color) == state.getBlock()) return Blocks.CONCRETE.pick(color).defaultBlockState();
			}
			// modded powders: guess the concrete by name
			Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
			Identifier concreteId = id.withPath(id.getPath().replace("_concrete_powder", "_concrete"));
			Block concrete = BuiltInRegistries.BLOCK.getValue(concreteId);
			if (concrete != Blocks.AIR && concrete != state.getBlock()) return concrete.defaultBlockState();
		}
		return null;
	}

	// ---------------- the actual transfer ----------------

	/** One checked transfer step, ready to be applied. */
	record Transfer(int cooldown, Runnable apply) {
	}

	/**
	 * Works out what one transfer step would do, without changing anything yet.
	 *
	 * @return null if there is nothing to move
	 */
	static @Nullable Transfer planTransfer(ServerLevel level, BlockPos pos, BlockState faucetState) {
		Direction facing = faucetState.getValue(FaucetBlock.FACING);
		Offer offer = findSource(level, pos, faucetState);
		if (offer != null) {
			BlockPos below = pos.below();
			for (long amount : TRANSFER_SIZES) {
				if (offer.drain().take(amount, true) != amount) continue;
				if (fill(level, below, offer, amount, true) != amount) continue;
				return new Transfer((int) (COOLDOWN_PER_BOTTLE * (amount / BOTTLE)), () -> {
					fill(level, below, offer, amount, false);
					offer.drain().take(amount, false);
				});
			}
			return null;
		}
		if (!faucetState.getValue(FaucetBlock.CONNECTED)) {
			Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos.relative(facing.getOpposite()), facing);
			if (storage != null && StorageUtil.findExtractableResource(storage, null) != null) {
				return new Transfer(COOLDOWN_PER_BOTTLE, () -> dropItem(level, pos, storage));
			}
		}
		return null;
	}

	/** Like the original: an open faucet on a container lets its items drop out one by one. */
	private static void dropItem(ServerLevel level, BlockPos pos, Storage<ItemVariant> storage) {
		ItemStack stack;
		try (Transaction tx = Transaction.openOuter()) {
			ResourceAmount<ItemVariant> extracted = StorageUtil.extractAny(storage, 1, tx);
			if (extracted == null || extracted.amount() == 0) return;
			stack = extracted.resource().toStack((int) extracted.amount());
			tx.commit();
		}
		ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, stack);
		drop.setDeltaMovement(Vec3.ZERO);
		level.addFreshEntity(drop);
		float pitch = 0.5F + (level.getRandom().nextFloat() - 0.5F) / 4F;
		level.playSound(null, pos, SoundEvents.CHICKEN_EGG, SoundSource.BLOCKS, 0.3F, pitch);
	}

	/** Whether the faucet should sit lowered, piping straight into the block below. */
	static boolean canConnectDown(Level level, BlockPos below) {
		BlockState state = level.getBlockState(below);
		if (state.getBlock() instanceof AbstractCauldronBlock) return false;
		if (state.hasProperty(BlockStateProperties.LEVEL_HONEY)) return true;
		return FluidStorage.SIDED.find(level, below, Direction.UP) != null;
	}
}
