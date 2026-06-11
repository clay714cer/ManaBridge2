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
    private static final Map<UUID, Double> lastIronsMana = new HashMap<>();
    private static final Map<UUID, Double> lastArsPercent = new HashMap<>();
    private static final Map<UUID, Integer> nativeArsMax = new HashMap<>();
    private static int tickCounter = 0;
    
    // === ИНИЦИАЛИЗАЦИЯ МАКСИМУМОВ ===
    public static void initMaxMana(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        int arsMax = getNativeArsMax(player);
        double ironsMax = getIronsMaxMana(player);
        
        nativeArsMax.put(playerId, arsMax);
        
        double totalMax = calculateMax(arsMax, ironsMax);
        setIronsMaxMana(player, totalMax);
        
        ManaBridge.LOGGER.debug("Max mana init: Ars={}, Iron's={}, Total={}", arsMax, ironsMax, totalMax);
    }
    
    // === ТИК СИНХРОНИЗАЦИИ ===
    public static void tick(ServerPlayer player) {
        tickCounter++;
        
        // Оптимизация: batch update
        if (Config.BATCH_UPDATE.get()) {
            if (tickCounter % 10 != 0) return;
        }
        
        UUID playerId = player.getUUID();
        
        // 1. Получаем текущие значения
        double ironsMana = getIronsMana(player);
        double ironsMax = getIronsMaxMana(player);
        int arsMax = nativeArsMax.getOrDefault(playerId, 100);
        
        if (ironsMax <= 0 || arsMax <= 0) return;
        
        // 2. Считаем процент для Ars
        double arsPercent = (ironsMana / ironsMax) * 100.0;
        
        // 3. Проверка на изменение
        if (Config.UPDATE_ON_CHANGE_ONLY.get()) {
            Double lastIrons = lastIronsMana.get(playerId);
            Double lastPercent = lastArsPercent.get(playerId);
            if (lastIrons != null && lastPercent != null) {
                double ironsDelta = Math.abs(ironsMana - lastIrons);
                double percentDelta = Math.abs(arsPercent - lastPercent);
                if (ironsDelta < 0.5 && percentDelta < 0.5) {
                    return;
                }
            }
        }
        
        // 4. Применяем направление синхронизации
        String direction = Config.SYNC_DIRECTION.get();
        
        switch (direction) {
            case "both":
                syncArsFromPercent(player, arsPercent, arsMax);
                break;
            case "ars_to_irons":
                double arsMana = getArsMana(player);
                double arsPercentFromArs = arsMax > 0 ? (arsMana / arsMax) * 100.0 : 0;
                setIronsMana(player, (arsPercentFromArs / 100.0) * ironsMax);
                break;
            case "irons_to_ars":
                syncArsFromPercent(player, arsPercent, arsMax);
                break;
        }
        
        lastIronsMana.put(playerId, ironsMana);
        lastArsPercent.put(playerId, arsPercent);
    }
    
    // === СИНХРОНИЗАЦИЯ ARS ИЗ ПРОЦЕНТОВ ===
    private static void syncArsFromPercent(ServerPlayer player, double percent, int arsMax) {
        double arsValue = (percent / 100.0) * arsMax;
        setArsMana(player, arsValue);
    }
    
    // === РАСЧЁТ МАКСИМУМА ===
    private static double calculateMax(int arsMax, double ironsMax) {
        double kArs = Config.K_ARS.get();
        double kIrons = Config.K_IRONS.get();
        double n = Config.N_VALUE.get();
        double total = (arsMax * kArs) + (ironsMax * kIrons) - n;
        
        double maxManaCap = Config.MAX_MANA_CAP.get();
        if (maxManaCap > 0 && total > maxManaCap) {
            total = maxManaCap;
        }
        
        return total;
    }
    
    // === ARS NOUVEAU ===
    private static int getNativeArsMax(ServerPlayer player) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            return manaCap != null ? manaCap.getMaxMana() : 100;
        } catch (Exception e) { return 100; }
    }
    
    private static double getArsMana(ServerPlayer player) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            return manaCap != null ? manaCap.getCurrentMana() : 0;
        } catch (Exception e) { return 0; }
    }
    
    private static void setArsMana(ServerPlayer player, double amount) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            if (manaCap != null) manaCap.setMana(amount);
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
