package com.clay.manabridge;

import com.clay.manabridge.config.Config;
import com.clay.manabridge.network.ManaSyncManager;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

@Mod(ManaBridge.MODID)
public class ManaBridge {
    public static final String MODID = "manabridge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ManaBridge(IEventBus modEventBus, ModContainer modContainer) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onEquipmentChange);
        NeoForge.EVENT_BUS.addListener(this::onEffectAdded);
        NeoForge.EVENT_BUS.addListener(this::onEffectRemoved);
        
        LOGGER.info("Mana Bridge initialized!");
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ManaSyncManager.syncCurrentMana(player);
        }
    }
    
    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        ManaSyncManager.updateMaxMana(event.getEntity());
    }
    
    private void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ManaSyncManager.updateMaxMana(player);
        }
    }
    
    private void onEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ManaSyncManager.updateMaxMana(player);
        }
    }
    
    private void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ManaSyncManager.updateMaxMana(player);
        }
    }
}
