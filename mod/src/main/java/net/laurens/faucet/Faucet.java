package net.laurens.faucet;

import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Standalone port of the Supplementaries faucet (by MehVahdJukaar & Plantkillable) for private use.
 */
public class Faucet implements ModInitializer {
	public static final String MOD_ID = "faucet";

	public static final FaucetBlock FAUCET = registerBlock("faucet", FaucetBlock::new);
	public static final Item FAUCET_ITEM = registerItem("faucet", FAUCET);

	public static final FaucetBlock INFINITE_WATER_FAUCET = registerBlock("infinite_water_faucet", InfiniteWaterFaucetBlock::new);
	public static final Item INFINITE_WATER_FAUCET_ITEM = registerItem("infinite_water_faucet", INFINITE_WATER_FAUCET);

	public static final SoundEvent FAUCET_SOUND = Registry.register(BuiltInRegistries.SOUND_EVENT, id("block.faucet.turn"),
			SoundEvent.createVariableRangeEvent(id("block.faucet.turn")));

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static <B extends Block> B registerBlock(String name, Function<BlockBehaviour.Properties, B> factory) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(BlockBehaviour.Properties.of()
				.setId(key)
				.mapColor(MapColor.METAL)
				.strength(3.0F, 4.8F)
				.sound(SoundType.METAL)
				.pushReaction(PushReaction.DESTROY)
				.noOcclusion()));
	}

	private static Item registerItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	@Override
	public void onInitialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS)
				.register(output -> output.insertAfter(Items.HOPPER, FAUCET_ITEM, INFINITE_WATER_FAUCET_ITEM));
	}
}
