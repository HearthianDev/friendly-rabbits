package net.hearthian.friendlyrabbits;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FriendlyRabbits implements ModInitializer {
	public static final String MOD_ID = "friendly-rabbits";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Making rabbits friendlier...");
	}
}