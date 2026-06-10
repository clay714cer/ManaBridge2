package com.clay.manabridge.network;

import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.clay.manabridge.ManaBridge;
import com.clay.manabridge.config.Config;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ManaSyncManager {
    private static final Map<UUID, Double> lastManaValues = new HashMap<>();
    private static int tickCounter = 0;
    
    public static void tick(Player player) {
        if (Config.BATCH_UPDATE.get()) {
            tickCounter++;
            if (tickCounter < 20) return;
            tickCounter = 0;
        }
        
        UUID playerId = player.getUUID();
        double sharedMana = calculateSharedMana(player);
        
        if (Config.UPDATE_ON_CHANGE_ONLY.get()) {
            Double lastMana = lastManaValues.get(playerId);
            if (lastMana != null && Math.abs(lastMana - sharedMana) < 0.01) {
                return;
            }
        }
        
        double maxMana = Config.MAX_MANA_CAP.get();
        if (maxMana > 0 && sharedMana > maxMana) {
            sharedMana = maxMana;
        }
        
        String direction = Config.SYNC_DIRECTION.get();
        
        switch (direction) {
            case "both":
                setArsMana(player, sharedMana);
                setIronsMana(player, sharedMana);
                break;
            case "ars_to_irons":
                double arsMana = getArsMana(player);
                setIronsMana(player, arsMana);
                break;
            case "irons_to_ars":
                double ironsMana = getIronsMana(player);
                setArsMana(player, ironsMana);
                break;
        }
        
        lastManaValues.put(playerId, sharedMana);
    }
    
    private static double calculateSharedMana(Player player) {
        double arsMana = getArsMana(player);
        double ironsMana = getIronsMana(player);
        
        double kArs = Config.K_ARS.get();
        double kIrons = Config.K_IRONS.get();
        double n = Config.N_VALUE.get();
        
        return (arsMana * kArs) + (ironsMana * kIrons) - n;
    }
    
    private static double getArsMana(Player player) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            if (manaCap != null) {
                return manaCap.getCurrentMana();
            }
        } catch (Exception e) {
            ManaBridge.LOGGER.error("Error getting Ars mana: ", e);
        }
        return 0.0;
    }
    
    private static double getIronsMana(Player player) {
        try {
            MagicData magicData = MagicData.getPlayerMagicData(player);
            if (magicData != null) {
                return magicData.getMana();
            }
        } catch (Exception e) {
            ManaBridge.LOGGER.error("Error getting Iron's mana: ", e);
        }
        return 0.0;
    }
    
    private static void setArsMana(Player player, double amount) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            if (manaCap != null) {
                manaCap.setMana(amount);
            }
        } catch (Exception e) {
            ManaBridge.LOGGER.error("Error setting Ars mana: ", e);
        }
    }
    
    private static void setIronsMana(Player player, double amount) {
        try {
            MagicData magicData = MagicData.getPlayerMagicData(player);
            if (magicData != null) {
                magicData.setMana((float) amount);
            }
        } catch (Exception e) {
            ManaBridge.LOGGER.error("Error setting Iron's mana: ", e);
        }
    }
}
