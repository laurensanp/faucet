package net.laurens.faucet.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.laurens.faucet.Faucet;
import net.laurens.faucet.FaucetBlock;
import net.laurens.faucet.Liquid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class FaucetGameTests {
	// Layout: faucet at (1,2,1) facing north, so its source is at (1,2,2) and its target at (1,1,1).
	private static final BlockPos FAUCET = new BlockPos(1, 2, 1);
	private static final BlockPos SOURCE = new BlockPos(1, 2, 2);
	private static final BlockPos TARGET = new BlockPos(1, 1, 1);

	private static void placeFaucet(GameTestHelper helper) {
		helper.setBlock(FAUCET, Faucet.FAUCET.defaultBlockState().setValue(FaucetBlock.FACING, Direction.NORTH));
	}

	private static void openFaucet(GameTestHelper helper) {
		helper.useBlock(FAUCET);
	}

	@GameTest(maxTicks = 200)
	public void waterCauldronFillsCauldronBelow(GameTestHelper helper) {
		helper.setBlock(SOURCE, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		helper.setBlock(TARGET, Blocks.CAULDRON);
		placeFaucet(helper);
		openFaucet(helper);
		helper.succeedWhen(() -> {
			helper.assertBlockState(TARGET, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
			helper.assertBlockPresent(Blocks.CAULDRON, SOURCE);
		});
	}

	@GameTest(maxTicks = 200)
	public void showsWaterWhileSourceHasWater(GameTestHelper helper) {
		helper.setBlock(SOURCE, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		placeFaucet(helper);
		openFaucet(helper);
		helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(FAUCET).getValue(FaucetBlock.LIQUID) == Liquid.WATER,
				"faucet should show water"));
	}

	@GameTest(maxTicks = 200)
	public void lavaCauldronMovesWholeBucket(GameTestHelper helper) {
		helper.setBlock(SOURCE, Blocks.LAVA_CAULDRON);
		helper.setBlock(TARGET, Blocks.CAULDRON);
		placeFaucet(helper);
		openFaucet(helper);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.LAVA_CAULDRON, TARGET);
			helper.assertBlockPresent(Blocks.CAULDRON, SOURCE);
		});
	}

	@GameTest(maxTicks = 200)
	public void waterSourceIsInfiniteAndMakesConcrete(GameTestHelper helper) {
		helper.setBlock(SOURCE, Blocks.WATER);
		helper.setBlock(TARGET, Blocks.CONCRETE_POWDER.pick(DyeColor.RED));
		placeFaucet(helper);
		openFaucet(helper);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.CONCRETE.pick(DyeColor.RED), TARGET);
			helper.assertBlockPresent(Blocks.WATER, SOURCE);
		});
	}

	@GameTest(maxTicks = 200)
	public void waterTurnsDirtIntoMud(GameTestHelper helper) {
		helper.setBlock(SOURCE, Blocks.WATER);
		helper.setBlock(TARGET, Blocks.DIRT);
		placeFaucet(helper);
		openFaucet(helper);
		helper.succeedWhenBlockPresent(Blocks.MUD, TARGET);
	}

	@GameTest(maxTicks = 200)
	public void redstoneOpensFaucet(GameTestHelper helper) {
		helper.setBlock(SOURCE, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 1));
		helper.setBlock(TARGET, Blocks.CAULDRON);
		placeFaucet(helper);
		helper.setBlock(FAUCET.east(), Blocks.REDSTONE_BLOCK);
		helper.succeedWhen(() -> {
			helper.assertBlockState(TARGET, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 1));
			helper.assertBlockPresent(Blocks.CAULDRON, SOURCE);
		});
	}

	@GameTest(maxTicks = 100)
	public void closedFaucetDoesNothing(GameTestHelper helper) {
		helper.setBlock(SOURCE, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		helper.setBlock(TARGET, Blocks.CAULDRON);
		placeFaucet(helper);
		helper.runAfterDelay(80, () -> {
			helper.assertBlockPresent(Blocks.CAULDRON, TARGET);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void honeyMovesBetweenHives(GameTestHelper helper) {
		helper.setBlock(SOURCE, Blocks.BEEHIVE.defaultBlockState().setValue(BlockStateProperties.LEVEL_HONEY, 5));
		helper.setBlock(TARGET, Blocks.BEEHIVE.defaultBlockState());
		placeFaucet(helper);
		openFaucet(helper);
		helper.succeedWhen(() -> {
			BlockState target = helper.getBlockState(TARGET);
			BlockState source = helper.getBlockState(SOURCE);
			helper.assertTrue(target.getValue(BlockStateProperties.LEVEL_HONEY) == 5, "target hive should be full");
			helper.assertTrue(source.getValue(BlockStateProperties.LEVEL_HONEY) == 0, "source hive should be empty");
		});
	}

	@GameTest(maxTicks = 200)
	public void powderSnowCauldronFillsCauldron(GameTestHelper helper) {
		helper.setBlock(SOURCE, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2));
		helper.setBlock(TARGET, Blocks.CAULDRON);
		placeFaucet(helper);
		openFaucet(helper);
		helper.succeedWhen(() -> {
			helper.assertBlockState(TARGET, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2));
			helper.assertBlockPresent(Blocks.CAULDRON, SOURCE);
		});
	}

	@GameTest(maxTicks = 200)
	public void chestItemsDropOut(GameTestHelper helper) {
		helper.setBlock(SOURCE, Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(SOURCE));
		chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
		placeFaucet(helper);
		openFaucet(helper);
		helper.succeedWhenEntityPresent(EntityTypes.ITEM, FAUCET);
	}

	@GameTest(maxTicks = 200)
	public void infiniteFaucetFillsCauldronWithNothingBehind(GameTestHelper helper) {
		helper.setBlock(TARGET, Blocks.CAULDRON);
		helper.setBlock(FAUCET, Faucet.INFINITE_WATER_FAUCET.defaultBlockState().setValue(FaucetBlock.FACING, Direction.NORTH));
		openFaucet(helper);
		helper.succeedWhen(() -> {
			helper.assertBlockState(TARGET, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
			helper.assertTrue(helper.getBlockState(FAUCET).getValue(FaucetBlock.LIQUID) == Liquid.WATER, "should show water");
		});
	}

	@GameTest(maxTicks = 200)
	public void infiniteFaucetMakesConcrete(GameTestHelper helper) {
		helper.setBlock(TARGET, Blocks.CONCRETE_POWDER.pick(DyeColor.BLUE));
		helper.setBlock(FAUCET, Faucet.INFINITE_WATER_FAUCET.defaultBlockState().setValue(FaucetBlock.FACING, Direction.NORTH));
		openFaucet(helper);
		helper.succeedWhenBlockPresent(Blocks.CONCRETE.pick(DyeColor.BLUE), TARGET);
	}

	@GameTest(maxTicks = 100)
	public void closedInfiniteFaucetDoesNothing(GameTestHelper helper) {
		helper.setBlock(TARGET, Blocks.CAULDRON);
		helper.setBlock(FAUCET, Faucet.INFINITE_WATER_FAUCET.defaultBlockState().setValue(FaucetBlock.FACING, Direction.NORTH));
		helper.runAfterDelay(80, () -> {
			helper.assertBlockPresent(Blocks.CAULDRON, TARGET);
			helper.succeed();
		});
	}
}
