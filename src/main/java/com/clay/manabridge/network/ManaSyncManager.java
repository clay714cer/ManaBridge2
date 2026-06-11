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
    // Отслеживание изменений
    private static final Map<UUID, Double> lastArsMana = new HashMap<>();
    private static final Map<UUID, Double> lastIronsMana = new HashMap<>();
    
    // Родные значения
    private static final Map<UUID, Integer> storedNativeArsMax = new HashMap<>();
    private static final Map<UUID, Double> storedNativeIronsMax = new HashMap<>();
    private static final Map<UUID, Double> storedNativeIronsRegen = new HashMap<>();
    
    // Умный мониторинг регенерации Ars
    private static final Map<UUID, Double> arsRegenMeasurements = new HashMap<>();
    private static final Map<UUID, Double> stableArsRegenBonus = new HashMap<>();
    private static final Map<UUID, Integer> regenSameCount = new HashMap<>();
    private static final Map<UUID, Boolean> regenMonitoringActive = new HashMap<>();
    
    private static int tickCounter = 0;
    private static final double ARS_BASE_REGEN = 3.0;
    
    // === ИНИЦИАЛИЗАЦИЯ ===
    public static void onPlayerLogin(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        if (!storedNativeArsMax.containsKey(playerId)) {
            storedNativeArsMax.put(playerId, 100);
        }
        if (!storedNativeIronsMax.containsKey(playerId)) {
            storedNativeIronsMax.put(playerId, 100.0);
        }
        if (!storedNativeIronsRegen.containsKey(playerId)) {
            double nativeRegen = getRawIronsRegen(player);
            storedNativeIronsRegen.put(playerId, nativeRegen);
        }
        
        regenMonitoringActive.put(playerId, true);
        regenSameCount.put(playerId, 0);
        
        applyMaxMana(player);
    }
    
    public static void onEquipmentChanged(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        double currentRegen = getRawIronsRegen(player);
        storedNativeIronsRegen.put(playerId, currentRegen);
        
        regenMonitoringActive.put(playerId, true);
        regenSameCount.put(playerId, 0);
        
        applyMaxMana(player);
    }
    
    // === ТИК ===
    public static void tick(ServerPlayer player) {
        tickCounter++;
        UUID playerId = player.getUUID();
        
        if (!storedNativeIronsMax.containsKey(playerId)) {
            onPlayerLogin(player);
            return;
        }
        
        // Проверка экипировки (раз в 5 сек)
        if (tickCounter % 100 == 0) {
            applyMaxMana(player);
        }
        
        // Мониторинг регенерации Ars (каждые 10 тиков)
        if (tickCounter % 10 == 0) {
            smartMeasureArsRegen(player);
        }
        
        // Синхронизация текущей маны
        if (Config.BATCH_UPDATE.get()) {
            if (tickCounter % 10 != 0) return;
        }
        
        double ironsMana = getIronsMana(player);
        double arsMana = getArsMana(player);
        double ironsMax = getIronsMaxMana(player);
        int arsMax = getActualArsMax(player);
        
        Double lastArs = lastArsMana.get(playerId);
        Double lastIrons = lastIronsMana.get(playerId);
        
        // === ОТСЛЕЖИВАНИЕ ARS ===
        if (lastArs != null) {
            double arsDelta = arsMana - lastArs;
            
            if (Math.abs(arsDelta) > 0.5) {
                double newIrons = ironsMana + arsDelta;
                
                if (newIrons > ironsMax) {
                    double overflow = newIrons - ironsMax;
                    newIrons = ironsMax;
                    setArsMana(player, Math.min(arsMax, arsMana + overflow));
                    arsMana = Math.min(arsMax, arsMana + overflow);
                }
                if (newIrons < 0) newIrons = 0;
                
                setIronsMana(player, newIrons);
                ironsMana = newIrons;
            }
        }
        
        // === ОТСЛЕЖИВАНИЕ IRON'S ===
        if (lastIrons != null) {
            double ironsDelta = ironsMana - lastIrons;
            
            if (Math.abs(ironsDelta) > 0.5) {
                double newArs = arsMana + ironsDelta;
                
                if (newArs > arsMax) {
                    double overflow = newArs - arsMax;
                    newArs = arsMax;
                    setIronsMana(player, Math.min(ironsMax, ironsMana + overflow));
                    ironsMana = Math.min(ironsMax, ironsMana + overflow);
                }
                if (newArs < 0) newArs = 0;
                
                setArsMana(player, newArs);
                arsMana = newArs;
            }
        }
        
        // Синхронизация по направлению
        String direction = Config.SYNC_DIRECTION.get();
        switch (direction) {
            case "ars_to_irons":
                setIronsMana(player, arsMana);
                break;
            case "irons_to_ars":
                setArsMana(player, ironsMana);
                break;
        }
        
        lastArsMana.put(playerId, arsMana);
        lastIronsMana.put(playerId, ironsMana);
    }
    
    // === УМНЫЙ МОНИТОРИНГ РЕГЕНЕРАЦИИ ARS ===
    private static void smartMeasureArsRegen(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        if (!regenMonitoringActive.getOrDefault(playerId, true)) return;
        
        double currentArsMana = getArsMana(player);
        Double previous = arsRegenMeasurements.get(playerId);
        
        if (previous != null) {
            double growth = currentArsMana - previous;
            double bonus = Math.max(0, growth - ARS_BASE_REGEN);
            
            int same = regenSameCount.getOrDefault(playerId, 0);
            double lastBonus = stableArsRegenBonus.getOrDefault(playerId, -1.0);
            
            if (Math.abs(bonus - lastBonus) < 0.1) {
                same++;
                regenSameCount.put(playerId, same);
                
                if (same >= 5) {
                    stableArsRegenBonus.put(playerId, bonus);
                    regenMonitoringActive.put(playerId, false);
                }
            } else {
                regenSameCount.put(playerId, 1);
                stableArsRegenBonus.put(playerId, bonus);
            }
        }
        
        arsRegenMeasurements.put(playerId, currentArsMana);
    }
    
    // === ПРИМЕНЕНИЕ МАКСИМУМА ===
    private static void applyMaxMana(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        int nativeArsMax = storedNativeArsMax.getOrDefault(playerId, 100);
        double nativeIronsMax = storedNativeIronsMax.getOrDefault(playerId, 100.0);
        int realArsMax = getActualArsMax(player);
        double realIronsMax = getIronsMaxMana(player);
        
        int arsBonus = Math.max(0, realArsMax - nativeArsMax);
        double ironsBonus = Math.max(0, realIronsMax - nativeIronsMax);
        
        double totalMax = calculateMax(nativeArsMax, nativeIronsMax) + arsBonus + ironsBonus;
        
        double cap = Config.MAX_MANA_CAP.get();
        if (cap > 0 && totalMax > cap) totalMax = cap;
        if (totalMax < 1) totalMax = 1;
        
        setIronsMaxMana(player, totalMax);
        setArsMaxMana(player, totalMax);
        
        double currentIrons = getIronsMana(player);
        double currentArs = getArsMana(player);
        if (currentIrons > totalMax) setIronsMana(player, totalMax);
        if (currentArs > totalMax) setArsMana(player, totalMax);
    }
    
    // === РАСЧЁТ МАКСИМУМА ===
    private static double calculateMax(int arsMax, double ironsMax) {
        double total = (arsMax * Config.K_ARS.get()) + (ironsMax * Config.K_IRONS.get()) - Config.N_VALUE.get();
        return Math.max(1, total);
    }
    
    // === ARS NOUVEAU ===
    private static int getActualArsMax(ServerPlayer player) {
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
    
    private static void setArsMaxMana(ServerPlayer player, double max) {
        try {
            IManaCap manaCap = CapabilityRegistry.getMana(player);
            if (manaCap != null) manaCap.setMaxMana((int) max);
        } catch (Exception e) {}
    }
    
    // === IRON'S SPELLS ===
    private static double getRawIronsRegen(ServerPlayer player) {
        try {
            var attr = player.getAttributes().getInstance(AttributeRegistry.MANA_REGEN);
            return attr != null ? attr.getBaseValue() : 1.0;
        } catch (Exception e) { return 1.0; }
    }
    
    private static double getIronsMana(ServerPlayer player) {
        try {
            MagicData magicData = MagicData.getPlayerMagicData(player);
            return magicData != null ? magicData.getMana() : 0;
        } catch (Exception e) { return 0; }
    }
    
    private static double getIronsMaxMana(ServerPlayer player) {
        try {
            var attr = player.getAttributes().getInstance(AttributeRegistry.MAX_MANA);
            return attr != null ? attr.getValue() : 100;
        } catch (Exception e) { return 100; }
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
    
    // === КОМАНДА ИНФО ===
    public static void showManaInfo(ServerPlayer player, net.minecraft.commands.CommandSourceStack source) {
        UUID playerId = player.getUUID();
        if (!storedNativeIronsMax.containsKey(playerId)) onPlayerLogin(player);
        
        double nativeMax = storedNativeIronsMax.getOrDefault(playerId, 100.0);
        double nativeRegen = storedNativeIronsRegen.getOrDefault(playerId, 1.0);
        double arsBonus = stableArsRegenBonus.getOrDefault(playerId, 0.0);
        int arsMax = getActualArsMax(player);
        double totalMax = getIronsMaxMana(player);
        double ironsMana = getIronsMana(player);
        double arsMana = getArsMana(player);
        double regenPerTick = (totalMax * nativeRegen * 0.01) + arsBonus;
        
        source.sendSuccess(() -> 
            net.minecraft.network.chat.Component.literal(
                "§6=== Mana Bridge Info ===\n" +
                "§bОбщий максимум: §f" + String.format("%.0f", totalMax) + "\n" +
                "§bТекущая мана: §f" + String.format("%.0f", ironsMana) + "/" + String.format("%.0f", totalMax) + "\n" +
                "§bРегенерация: §f" + String.format("%.1f", regenPerTick) + "/раз\n" +
                "§bArs макс: §f" + arsMax + " | §bТекущая: §f" + String.format("%.0f", arsMana) + "\n" +
                "§bIron's родной: §f" + String.format("%.0f", nativeMax) + " | §bРеген: §f×" + String.format("%.2f", nativeRegen) + "\n" +
                "§bБонус регена Ars: §f+" + String.format("%.1f", arsBonus) + "\n" +
                "§bМножители: §fArs ×" + Config.K_ARS.get() + ", Iron's ×" + Config.K_IRONS.get()
            ), false
        );
    }
}
