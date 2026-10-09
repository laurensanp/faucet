package net.laurens.faucet;

import net.minecraft.util.StringRepresentable;

/**
 * What the faucet is currently showing. Purely visual, stored in the block state.
 */
public enum Liquid implements StringRepresentable {
	NONE("none"),
	WATER("water"),
	LAVA("lava"),
	HONEY("honey"),
	POWDER_SNOW("powder_snow"),
	OTHER("other");

	private final String name;

	Liquid(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
