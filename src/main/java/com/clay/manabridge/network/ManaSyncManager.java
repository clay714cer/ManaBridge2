package com.clay.manabridge.network;

import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.clay.manabridge.config.Config;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ManaSyncManager {
    private static final Map<UUID, Double> lastArsMana = new HashMap<>();
    private static final Map<UUID, Double> nativeIronsMax = new HashMap<>();
    private static final Map<UUID, Integer> idleTicks = new HashMap<>();
    private static final Map<UUID, Double> idleCheckMana = new HashMap<>();
    private static int tickCounter = 0;
    
    public static void tick(ServerPlayer player) {
        tickCounter++;
        if (tickCounter % 2 != 0) return;
        
        UUID playerId = player.getUUID();
        
        double ironsMana = getIronsMana(player);
        double ironsMax = getIronsMaxMana(player);
        int realArsMax = getArsMaxMana(player);
        double arsMana = getArsMana(player);
        
        if (ironsMax <= 0 || realArsMax <= 0) return;
        
        // Сохраняем родной максимум Iron's один раз
        if (!nativeIronsMax.containsKey(playerId)) {
            nativeIronsMax.put(playerId, 100.0);
        }
        
        // Раз в 5 секунд — добавить бонус от Ars к Iron's
        if (tickCounter % 100 == 0) {
            int arsBonus = Math.max(0, realArsMax - 100);
            double baseMax = nativeIronsMax.getOrDefault(playerId, 100.0);
            double newMax = baseMax + arsBonus;
            setIronsMaxMana(player, newMax);
            ironsMax = newMax;
        }
        
        Double lastArs = lastArsMana.get(playerId);
        
        if (lastArs == null) {
            double ironsPercent = ironsMana / ironsMax;
            setArsMana(player, ironsPercent * realArsMax);
            lastArsMana.put(playerId, getArsMana(player));
            return;
        }
        
        double arsDelta = arsMana - lastArs;
        
        if (Math.abs(arsDelta) > 0.01) {
            double newIrons = ironsMana + arsDelta;
            if (newIrons < 0) newIrons = 0;
            if (newIrons > ironsMax) newIrons = ironsMax;
            setIronsMana(player, newIrons);
        } else {
            double ironsPercent = getIronsMana(player) / ironsMax;
            setArsMana(player, ironsPercent * realArsMax);
        }
        
            
        // Корректировка неполного заполнения
        double currentMana = getIronsMana(player);
        double maxMana = getIronsMaxMana(player);
        
        if (maxMana - currentMana > 0 && (maxMana - currentMana) / maxMana < 0.05) {
            Double lastCheck = idleCheckMana.get(playerId);
            if (lastCheck != null && Math.abs(currentMana - lastCheck) < 0.01) {
                int ticks = idleTicks.getOrDefault(playerId, 0) + 1;
                if (ticks >= 15) {
                    setIronsMana(player, maxMana);
                    setArsMana(player, getArsMaxMana(player));
                    idleTicks.put(playerId, 0);
                } else {
                    idleTicks.put(playerId, ticks);
                }
            } else {
                idleTicks.put(playerId, 0);
            }
            idleCheckMana.put(playerId, currentMana);
        } else {
            idleTicks.put(playerId, 0);
        }
        
        lastArsMana.put(playerId, getArsMana(player));
    }
    
    public static void showManaInfo(ServerPlayer player, net.minecraft.commands.CommandSourceStack source) {
        double ironsMana = getIronsMana(player);
        double ironsMax = getIronsMaxMana(player);
        double arsMana = getArsMana(player);
        int arsMax = getArsMaxMana(player);
        
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
            "§6=== Mana Bridge ===\n" +
            "§bОбщий пул (Iron's): §f" + String.format("%.0f", ironsMana) + "/" + String.format("%.0f", ironsMax) + "\n" +
            "§bArs: §f" + String.format("%.0f", arsMana) + "/" + arsMax + " (" + String.format("%.0f", (arsMana/arsMax)*100) + "%)"
        ), false);
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
        try { IManaCap m = CapabilityRegistry.getMana(player); if (m != null) m.setMana(amount); }
        catch (Exception e) {}
    }
    
    private static double getIronsMana(ServerPlayer player) {
        try { MagicData m = MagicData.getPlayerMagicData(player); return m != null ? m.getMana() : 0; }
        catch (Exception e) { return 0; }
    }
    
    private static double getIronsMaxMana(ServerPlayer player) {
        try { var a = player.getAttributes().getInstance(AttributeRegistry.MAX_MANA); return a != null ? a.getValue() : 100; }
        catch (Exception e) { return 100; }
    }
    
    private static void setIronsMana(ServerPlayer player, double amount) {
        try { MagicData m = MagicData.getPlayerMagicData(player); if (m != null) m.setMana((float) amount); }
        catch (Exception e) {}
    }
    
    private static void setIronsMaxMana(ServerPlayer player, double max) {
        try { var a = player.getAttributes().getInstance(AttributeRegistry.MAX_MANA); if (a != null) a.setBaseValue(max); }
        catch (Exception e) {}
    }
}
