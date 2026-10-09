package net.laurens.faucet.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Builds a little faucet showcase and screenshots it, to check the models render. */
public class FaucetClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			String[] commands = {
					"time set noon",
					"weather clear",
					// faucets first (open), sources placed after so the faucets notice them
					"setblock -3 -59 3 faucet:faucet[facing=north,enabled=true]",
					"setblock -1 -59 3 faucet:faucet[facing=north,enabled=true]",
					"setblock 1 -59 3 faucet:faucet[facing=north,enabled=true]",
					"setblock 3 -59 3 faucet:faucet[facing=north,enabled=true]",
					"setblock 5 -59 3 faucet:faucet[facing=north,enabled=false]",
					"setblock 7 -59 3 faucet:faucet[facing=north,enabled=true]",
					"setblock -3 -60 4 stone", "setblock -3 -59 4 water_cauldron[level=3]",
					"setblock -1 -60 4 stone", "setblock -1 -59 4 lava_cauldron",
					"setblock 1 -60 4 stone", "setblock 1 -59 4 beehive[facing=north,honey_level=5]",
					"setblock 3 -60 4 stone", "setblock 3 -59 4 powder_snow_cauldron[level=3]",
					"setblock 5 -60 4 stone", "setblock 5 -59 4 water_cauldron[level=3]",
					"setblock 7 -60 3 beehive[facing=north]", "setblock 7 -60 4 stone", "setblock 7 -59 4 water_cauldron[level=3]",
					"setblock -5 -59 3 faucet:infinite_water_faucet[facing=north,enabled=true]", "setblock -5 -60 3 cauldron",
					"setblock -7 -59 3 faucet:infinite_water_faucet[facing=north,enabled=false]",
					"tp @p 0 -60 -4 0 25"
			};
			for (String command : commands) {
				world.getServer().runCommand(command);
			}
			context.waitTicks(10);
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("faucet_showcase");
			world.getServer().runCommand("tp @p 2 -59 1 0 30");
			context.waitTicks(10);
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("faucet_closeup");
		}
	}
}
