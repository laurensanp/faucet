package net.laurens.faucet;

import com.mojang.serialization.MapCodec;

/** A faucet that needs nothing behind it: it always pours water. */
public class InfiniteWaterFaucetBlock extends FaucetBlock {
	public static final MapCodec<InfiniteWaterFaucetBlock> CODEC = simpleCodec(InfiniteWaterFaucetBlock::new);

	public InfiniteWaterFaucetBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<InfiniteWaterFaucetBlock> codec() {
		return CODEC;
	}
}
