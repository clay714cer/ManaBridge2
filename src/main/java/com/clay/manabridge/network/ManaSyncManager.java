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
    private static int tickCounter = 0;
    private static final int ARS_MAX = 100;
    
    // Основной метод, вызывается из ManaBridge каждый тик
    public static void tick(ServerPlayer player) {
        tickCounter++;
        if (tickCounter % 10 != 0) return; // раз в 10 тиков
        
        UUID playerId = player.getUUID();
        
        double ironsMana = getIronsMana(player);
        double ironsMax = getIronsMaxMana(player);
        double arsMana = getArsMana(player);
        
        if (ironsMax <= 0) return;
        
        // Принудительно фиксируем максимум Ars
        setArsMaxMana(player);
        
        Double lastArs = lastArsMana.get(playerId);
        
        if (lastArs == null) {
            // Первый запуск — синхронизируем
            double ironsPercent = ironsMana / ironsMax;
            setArsMana(player, ironsPercent * ARS_MAX);
            lastArsMana.put(playerId, getArsMana(player));
            return;
        }
        
        double arsDelta = arsMana - lastArs;
        
        if (Math.abs(arsDelta) > 0.01) {
            // Ars изменился — применяем к Iron's
            double newIrons = ironsMana + arsDelta;
            if (newIrons < 0) newIrons = 0;
            if (newIrons > ironsMax) newIrons = ironsMax;
            setIronsMana(player, newIrons);
        } else {
            // Iron's мог измениться — обновляем Ars
            double ironsPercent = getIronsMana(player) / ironsMax;
            setArsMana(player, ironsPercent * ARS_MAX);
        }
        
        lastArsMana.put(playerId, getArsMana(player));
    }
    
    // Команда /manabridge info
    public static void showManaInfo(ServerPlayer player, net.minecraft.commands.CommandSourceStack source) {
        double ironsMana = getIronsMana(player);
        double ironsMax = getIronsMaxMana(player);
        double arsMana = getArsMana(player);
        
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
            "§6=== Mana Bridge ===\n" +
            "§bОбщий пул (Iron's): §f" + String.format("%.0f", ironsMana) + "/" + String.format("%.0f", ironsMax) + "\n" +
            "§bArs: §f" + String.format("%.0f", arsMana) + "/" + ARS_MAX + " (" + String.format("%.0f", (arsMana/ARS_MAX)*100) + "%)"
        ), false);
    }
    
    // === ARS ===
    private static double getArsMana(ServerPlayer player) {
        try { IManaCap m = CapabilityRegistry.getMana(player); return m != null ? m.getCurrentMana() : 0; }
        catch (Exception e) { return 0; }
    }
    
    private static void setArsMana(ServerPlayer player, double amount) {
        try { IManaCap m = CapabilityRegistry.getMana(player); if (m != null) m.setMana(amount); }
        catch (Exception e) {}
    }
    
    private static void setArsMaxMana(ServerPlayer player) {
        try { IManaCap m = CapabilityRegistry.getMana(player); if (m != null) m.setMaxMana(ARS_MAX); }
        catch (Exception e) {}
    }
    
    // === IRON'S ===
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
}
