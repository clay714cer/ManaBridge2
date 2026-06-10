package com.clay.manabridge;

import com.clay.manabridge.config.Config;
import com.clay.manabridge.network.ManaSyncManager;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

@Mod(ManaBridge.MODID)
public class ManaBridge {
    public static final String MODID = "manabridge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ManaBridge(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);

        LOGGER.info("Mana Bridge initialized!");
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;

        ManaSyncManager.tick(event.getEntity());
    }
}