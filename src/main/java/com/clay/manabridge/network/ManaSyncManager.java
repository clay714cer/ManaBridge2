package com.clay.manabridge.network;

import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.clay.manabridge.ManaBridge;
import com.clay.manabridge.config.Config;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ManaSyncManager {
    private static final Map<UUID, Double> lastCurrentMana = new HashMap<>();
    private static final Map<UUID, Integer> nativeArsMaxMana = new HashMap<>();
    private static int tickCounter = 0;
    
    public static void updateMaxMana(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        int arsMax = getArsMaxMana(player);
        double ironsMax = getIronsMaxMana(player);
        
        nativeArsMaxMana.put(playerId, arsMax);
        
        double totalMax = arsMax * Config.K_ARS_MAX.get() + ironsMax * Config.K_IRONS_MAX.get();
        
        double maxManaCap = Config.MAX_MANA_CAP.get();
        if (maxManaCap > 0 && totalMax > maxManaCap) {
            totalMax = maxManaCap;
        }
        
        setArsMaxMana(player, (int) totalMax);
        setIronsMaxMana(player, totalMax);
    }
    
    public static void syncCurrentMana(ServerPlayer player) {
        tickCounter++;
        int interval = Config.CURRENT_MANA_SYNC_INTERVAL.get();
        if (interval > 0 && tickCounter % interval != 0) return;
        
        UUID playerId = player.getUUID();
        
        double arsMana = getArsMana(player);
        double ironsMana = getIronsMana(player);
        int arsMax = getArsMaxMana(player);
        double ironsMax = getIronsMaxMana(player);
        
        if (arsMax <= 0 || ironsMax <= 0) return;
        
        double sharedCurrent = arsMana * Config.K_ARS_CURRENT.get() + ironsMana * Config.K_IRONS_CURRENT.get();
        
        double totalMax = arsMax + ironsMax;
        if (sharedCurrent > totalMax) sharedCurrent = totalMax;
        if (sharedCurrent < 0) sharedCurrent = 0;
        
        double maxManaCap = Config.MAX_MANA_CAP.get();
        if (maxManaCap > 0 && sharedCurrent > maxManaCap) {
            sharedCurrent = maxManaCap;
        }
        
        if (Config.UPDATE_ON_CHANGE_ONLY.get()) {
            Double last = lastCurrentMana.get(playerId);
            if (last != null && Math.abs(last - sharedCurrent) < 0.5) {
                return;
            }
        }
        
        String direction = Config.SYNC_DIRECTION.get();
        switch (direction) {
            case "both":
                setArsMana(player, sharedCurrent);
                setIronsMana(player, sharedCurrent);
                break;
            case "ars_to_irons":
                setIronsMana(player, arsMana);
                break;
            case "irons_to_ars":
                setArsMana(player, ironsMana);
                break;
        }
        
        lastCurrentMana.put(playerId, sharedCurrent);
    }
    
    // === ARS NOUVEAU ===
    private static double getArsMana(ServerPlayer player) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            return manaCap != null ? manaCap.getCurrentMana() : 0;
        } catch (Exception e) { return 0; }
    }
    
    private static int getArsMaxMana(ServerPlayer player) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            return manaCap != null ? manaCap.getMaxMana() : 100;
        } catch (Exception e) { return 100; }
    }
    
    private static void setArsMana(ServerPlayer player, double amount) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            if (manaCap != null) manaCap.setMana(amount);
        } catch (Exception e) {}
    }
    
    private static void setArsMaxMana(ServerPlayer player, int max) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            if (manaCap != null) manaCap.setMaxMana(max);
        } catch (Exception e) {}
    }
    
    // === IRON'S SPELLS ===
    private static double getIronsMana(ServerPlayer player) {
        try {
            MagicData magicData = MagicData.getPlayerMagicData(player);
            return magicData != null ? magicData.getMana() : 0;
        } catch (Exception e) { return 0; }
    }
    
    private static double getIronsMaxMana(ServerPlayer player) {
        try {
            var attr = player.getAttributes().getInstance(AttributeRegistry.MAX_MANA);
            return attr != null ? attr.getValue() : 200;
        } catch (Exception e) { return 200; }
    }
    
    private static void setIronsMana(ServerPlayer player, double amount) {
        try {
            MagicData magicData = MagicData.getPlayerMagicData(player);
            if (magicData != null) magicData.setMana((float) amount);
        } catch (Exception e) {}
    }
    
    private static void setIronsMaxMana(ServerPlayer player, double max) {
        try {
            var attr = player.getAttributes().getInstance(AttributeRegistry.MAX_MANA);
            if (attr != null) attr.setBaseValue(max);
        } catch (Exception e) {}
    }
}
