package net.laurens.faucet.client;

import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.laurens.faucet.Faucet;
import net.minecraft.client.color.block.BlockTintSources;

public class FaucetClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// tint index 0 is only used by the water stream, so it picks up the biome water color
		BlockColorRegistry.register(List.of(BlockTintSources.water()), Faucet.FAUCET, Faucet.INFINITE_WATER_FAUCET);
	}
}
