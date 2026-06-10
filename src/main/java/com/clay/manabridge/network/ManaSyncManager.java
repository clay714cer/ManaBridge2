package com.clay.manabridge.network;

import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.clay.manabridge.ManaBridge;
import com.clay.manabridge.config.Config;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ManaSyncManager {
    private static final Map<UUID, Double> lastManaValues = new HashMap<>();
    private static final Map<UUID, Double> lastMaxManaValues = new HashMap<>();
    private static int tickCounter = 0;
    
    public static void tick(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        
        if (Config.BATCH_UPDATE.get()) {
            tickCounter++;
            if (tickCounter < 20) return;
            tickCounter = 0;
        }
        
        UUID playerId = player.getUUID();
        
        // 1. Расчёт общей максимальной маны по формуле
        double arsMaxMana = getArsMaxMana(player);
        double ironsMaxMana = getIronsMaxMana(serverPlayer);
        double sharedMaxMana = calculateSharedValue(arsMaxMana, ironsMaxMana);
        
        // 2. Ограничение максимальной маны
        double maxManaCap = Config.MAX_MANA_CAP.get();
        if (maxManaCap > 0 && sharedMaxMana > maxManaCap) {
            sharedMaxMana = maxManaCap;
        }
        
        // 3. Применяем общую максимальную ману к обоим модам
        setArsMaxMana(player, (int) sharedMaxMana);
        setIronsMaxMana(serverPlayer, sharedMaxMana);
        
        // 4. Расчёт текущей общей маны
        double arsMana = getArsMana(player);
        double ironsMana = getIronsMana(player);
        double sharedMana = calculateSharedValue(arsMana, ironsMana);
        
        // 5. Проверка на изменение
        if (Config.UPDATE_ON_CHANGE_ONLY.get()) {
            Double lastMana = lastManaValues.get(playerId);
            if (lastMana != null && Math.abs(lastMana - sharedMana) < 0.01) {
                return;
            }
        }
        
        // 6. Ограничение текущей маны
        if (sharedMana > sharedMaxMana) {
            sharedMana = sharedMaxMana;
        }
        
        // 7. Синхронизация
        String direction = Config.SYNC_DIRECTION.get();
        
        switch (direction) {
            case "both":
                setArsMana(player, sharedMana);
                setIronsMana(player, sharedMana);
                break;
            case "ars_to_irons":
                setIronsMana(player, arsMana);
                break;
            case "irons_to_ars":
                setArsMana(player, ironsMana);
                break;
        }
        
        lastManaValues.put(playerId, sharedMana);
    }
    
    // Общая формула для расчёта
    private static double calculateSharedValue(double arsValue, double ironsValue) {
        double kArs = Config.K_ARS.get();
        double kIrons = Config.K_IRONS.get();
        double n = Config.N_VALUE.get();
        return (arsValue * kArs) + (ironsValue * kIrons) - n;
    }
    
    // === Ars Nouveau ===
    private static double getArsMana(Player player) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            if (manaCap != null) return manaCap.getCurrentMana();
        } catch (Exception e) {
            ManaBridge.LOGGER.error("Error getting Ars mana: ", e);
        }
        return 0.0;
    }
    
    private static double getArsMaxMana(Player player) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            if (manaCap != null) return manaCap.getMaxMana();
        } catch (Exception e) {
            ManaBridge.LOGGER.error("Error getting Ars max mana: ", e);
        }
        return 0.0;
    }
    
    private static void setArsMana(Player player, double amount) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            if (manaCap != null) manaCap.setMana(amount);
        } catch (Exception e) {
            ManaBridge.LOGGER.error("Error setting Ars mana: ", e);
        }
    }
    
    private static void setArsMaxMana(Player player, int maxMana) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            if (manaCap != null) manaCap.setMaxMana(maxMana);
        } catch (Exception e) {
            ManaBridge.LOGGER.error("Error setting Ars max mana: ", e);
        }
    }
    
    // === Iron's Spells ===
    private static double getIronsMana(Player player) {
        try {
            MagicData magicData = MagicData.getPlayerMagicData(player);
            if (magicData != null) return magicData.getMana();
        } catch (Exception e) {
            ManaBridge.LOGGER.error("Error getting Iron's mana: ", e);
        }
        return 0.0;
    }
    
    private static double getIronsMaxMana(ServerPlayer player) {
        try {
            AttributeInstance attr = player.getAttribute(AttributeRegistry.MAX_MANA);
            if (attr != null) return attr.getValue();
        } catch (Exception e) {
            ManaBridge.LOGGER.error("Error getting Iron's max mana: ", e);
        }
        return 0.0;
    }
    
    private static void setIronsMana(Player player, double amount) {
        try {
            MagicData magicData = MagicData.getPlayerMagicData(player);
            if (magicData != null) magicData.setMana((float) amount);
        } catch (Exception e) {
            ManaBridge.LOGGER.error("Error setting Iron's mana: ", e);
        }
    }
    
    private static void setIronsMaxMana(ServerPlayer player, double maxMana) {
        try {
            AttributeInstance attr = player.getAttribute(AttributeRegistry.MAX_MANA);
            if (attr != null) {
                attr.setBaseValue(maxMana);
            }
        } catch (Exception e) {
            ManaBridge.LOGGER.error("Error setting Iron's max mana: ", e);
        }
    }
}
