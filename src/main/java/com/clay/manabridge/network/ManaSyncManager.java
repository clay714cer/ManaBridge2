package com.clay.manabridge.network;

import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.clay.manabridge.ManaBridge;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ManaSyncManager {
    private static final Map<UUID, Double> lastArsMana = new HashMap<>();
    private static final Map<UUID, Double> lastIronsMana = new HashMap<>();
    private static int tickCounter = 0;
    private static final int ARS_BASE_MAX = 100;
    
    public static void tick(ServerPlayer player) {
        tickCounter++;
        if (tickCounter % 10 != 0) return;
        
        UUID playerId = player.getUUID();
        
        double ironsMana = getIronsMana(player);
        double ironsMax = getIronsMaxMana(player);
        double arsMana = getArsMana(player);
        int arsMax = getArsMaxMana(player);
        
        if (ironsMax <= 0 || arsMax <= 0) return;
        
        Double lastArs = lastArsMana.get(playerId);
        Double lastIrons = lastIronsMana.get(playerId);
        
        if (lastArs == null || lastIrons == null) {
            lastArsMana.put(playerId, arsMana);
            lastIronsMana.put(playerId, ironsMana);
            return;
        }
        
        double arsDelta = arsMana - lastArs;
        double ironsDelta = ironsMana - lastIrons;
        
        // Кто изменился сильнее — тот ведущий
        if (Math.abs(arsDelta) >= Math.abs(ironsDelta) && Math.abs(arsDelta) > 0.01) {
            // Ars изменился — пересчитываем в проценты и применяем к Iron's
            double arsPercent = arsMana / arsMax;
            double newIrons = arsPercent * ironsMax;
            setIronsMana(player, newIrons);
            lastIronsMana.put(playerId, newIrons);
            lastArsMana.put(playerId, arsMana);
        } else if (Math.abs(ironsDelta) > 0.01) {
            // Iron's изменился — пересчитываем в проценты и применяем к Ars
            double ironsPercent = ironsMana / ironsMax;
            double newArs = ironsPercent * arsMax;
            setArsMana(player, newArs);
            lastArsMana.put(playerId, newArs);
            lastIronsMana.put(playerId, ironsMana);
        }
    }
    
    private static double getArsMana(ServerPlayer player) {
        try { IManaCap m = CapabilityRegistry.getMana(player); return m != null ? m.getCurrentMana() : 0; }
        catch (Exception e) { return 0; }
    }
    
    private static int getArsMaxMana(ServerPlayer player) {
        try { IManaCap m = CapabilityRegistry.getMana(player); return m != null ? m.getMaxMana() : 100; }
        catch (Exception e) { return 100; }
    }
    
    private static void setArsMana(ServerPlayer player, double amount) {
        try {
            IManaCap m = CapabilityRegistry.getMana(player);
            if (m != null) m.setMana(amount);
        } catch (Exception e) {}
    }
    
    private static double getIronsMana(ServerPlayer player) {
        try { MagicData m = MagicData.getPlayerMagicData(player); return m != null ? m.getMana() : 0; }
        catch (Exception e) { return 0; }
    }
    
    private static double getIronsMaxMana(ServerPlayer player) {
        try {
            var a = player.getAttributes().getInstance(AttributeRegistry.MAX_MANA);
            return a != null ? a.getValue() : 100;
        } catch (Exception e) { return 100; }
    }
    
    private static void setIronsMana(ServerPlayer player, double amount) {
        try {
            MagicData m = MagicData.getPlayerMagicData(player);
            if (m != null) m.setMana((float) amount);
        } catch (Exception e) {}
    }
}
