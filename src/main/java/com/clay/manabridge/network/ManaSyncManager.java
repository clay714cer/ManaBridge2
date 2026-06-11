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
    private static final Map<UUID, Double> storedNativeIronsMax = new HashMap<>();
    private static final Map<UUID, Double> storedNativeIronsRegen = new HashMap<>();
    private static int tickCounter = 0;
    private static final int ARS_FIXED_MAX = 100;
    
    // === ИНИЦИАЛИЗАЦИЯ (вызывается при входе и эффектах) ===
    public static void initMaxMana(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        if (!storedNativeIronsMax.containsKey(playerId)) {
            double nativeMax = getIronsMaxMana(player);
            double nativeRegen = getRawIronsRegen(player);
            storedNativeIronsMax.put(playerId, nativeMax);
            storedNativeIronsRegen.put(playerId, nativeRegen);
        }
        
        applyMaxMana(player);
    }
    
    // === ТИК СИНХРОНИЗАЦИИ ===
    public static void tick(ServerPlayer player) {
        tickCounter++;
        
        if (Config.BATCH_UPDATE.get()) {
            if (tickCounter % 10 != 0) return;
        }
        
        UUID playerId = player.getUUID();
        
        if (!storedNativeIronsMax.containsKey(playerId)) {
            initMaxMana(player);
            return;
        }
        
        double ironsMana = getIronsMana(player);
        double ironsMax = getIronsMaxMana(player);
        
        if (ironsMax <= 0) return;
        
        double currentArsMana = getArsMana(player);
        double currentArsPercent = ARS_FIXED_MAX > 0 ? (currentArsMana / ARS_FIXED_MAX) * 100.0 : 0;
        double ironsPercent = ironsMax > 0 ? (ironsMana / ironsMax) * 100.0 : 0;
        
        Double lastPercent = lastArsPercent.get(playerId);
        Double lastIrons = lastIronsMana.get(playerId);
        
        // === ОТСЛЕЖИВАНИЕ ИЗМЕНЕНИЙ ARS ===
        if (lastPercent != null && currentArsPercent < lastPercent - 0.1) {
            double deltaArsMana = (lastPercent - currentArsPercent) / 100.0 * ARS_FIXED_MAX;
            double newIronsMana = Math.max(0, ironsMana - deltaArsMana);
            setIronsMana(player, newIronsMana);
            ironsMana = newIronsMana;
            ironsPercent = ironsMax > 0 ? (ironsMana / ironsMax) * 100.0 : 0;
        }
        
        // === ОТСЛЕЖИВАНИЕ ИЗМЕНЕНИЙ IRON'S ===
        if (lastIrons != null && ironsMana < lastIrons - 0.5) {
            syncArsFromPercent(player, ironsPercent);
            currentArsPercent = ironsPercent;
        }
        
        // === ПРОВЕРКА НА ИЗМЕНЕНИЕ ===
        if (Config.UPDATE_ON_CHANGE_ONLY.get()) {
            if (lastPercent != null && lastIrons != null) {
                double percentDelta = Math.abs(currentArsPercent - lastPercent);
                double ironsDelta = Math.abs(ironsMana - lastIrons);
                if (percentDelta < 0.5 && ironsDelta < 0.5) {
                    return;
                }
            }
        }
        
        // === СИНХРОНИЗАЦИЯ ===
        String direction = Config.SYNC_DIRECTION.get();
        switch (direction) {
            case "both":
                syncArsFromPercent(player, ironsPercent);
                break;
            case "ars_to_irons":
                setIronsMana(player, (currentArsPercent / 100.0) * ironsMax);
                break;
            case "irons_to_ars":
                syncArsFromPercent(player, ironsPercent);
                break;
        }
        
        lastIronsMana.put(playerId, ironsMana);
        lastArsPercent.put(playerId, ironsPercent);
    }
    
    // === ПРИМЕНЕНИЕ МАКСИМУМА ===
    private static void applyMaxMana(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        double nativeMax = storedNativeIronsMax.getOrDefault(playerId, 200.0);
        double nativeRegen = storedNativeIronsRegen.getOrDefault(playerId, 1.0);
        
        double totalMax = calculateMax(ARS_FIXED_MAX, nativeMax);
        setIronsMaxMana(player, totalMax);
        
        double currentMana = getIronsMana(player);
        if (currentMana > totalMax) {
            setIronsMana(player, totalMax);
        }
    }
    
    // === СИНХРОНИЗАЦИЯ ARS ===
    private static void syncArsFromPercent(ServerPlayer player, double percent) {
        double arsValue = (percent / 100.0) * ARS_FIXED_MAX;
        setArsMana(player, arsValue);
    }
    
    // === РАСЧЁТ МАКСИМУМА ===
    private static double calculateMax(int arsMax, double ironsMax) {
        double kArs = Config.K_ARS.get();
        double kIrons = Config.K_IRONS.get();
        double n = Config.N_VALUE.get();
        double total = (arsMax * kArs) + (ironsMax * kIrons) - n;
        
        double maxManaCap = Config.MAX_MANA_CAP.get();
        if (maxManaCap > 0 && total > maxManaCap) total = maxManaCap;
        
        return Math.max(1, total);
    }
    
    // === ARS NOUVEAU ===
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
    private static double getRawIronsMax(ServerPlayer player) {
        try {
            var attr = player.getAttributes().getInstance(AttributeRegistry.MAX_MANA);
            return attr != null ? attr.getBaseValue() : 200;
        } catch (Exception e) { return 200; }
    }
    
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
    
    // === КОМАНДА ИНФО ===
    public static void showManaInfo(ServerPlayer player, net.minecraft.commands.CommandSourceStack source) {
        UUID playerId = player.getUUID();
        
        if (!storedNativeIronsMax.containsKey(playerId)) {
            initMaxMana(player);
        }
        
        double nativeMax = storedNativeIronsMax.getOrDefault(playerId, 200.0);
        double nativeRegen = storedNativeIronsRegen.getOrDefault(playerId, 1.0);
        double totalMax = getIronsMaxMana(player);
        double ironsMana = getIronsMana(player);
        
       double regenPerSecond = (totalMax * 0.01) * 20; // 1% от макс в тик × 20 тиков
        
        source.sendSuccess(() -> 
            net.minecraft.network.chat.Component.literal(
                "§6=== Mana Bridge Info ===\n" +
                "§bОбщий максимум: §f" + String.format("%.0f", totalMax) + "\n" +
                "§bТекущая мана: §f" + String.format("%.0f", ironsMana) + "/" + String.format("%.0f", totalMax) + "\n" +
                "§bРегенерация: §f" + String.format("%.1f", regenPerSecond) + "/сек\n" +
                "§bОт Ars: §f+" + ARS_FIXED_MAX + " макс (фикс.)\n" +
                "§bОт Iron's: §f+" + String.format("%.0f", nativeMax) + " макс (родной)\n" +
                "§bМножители: §fArs ×" + Config.K_ARS.get() + ", Iron's ×" + Config.K_IRONS.get()
            ), false
        );
    }
}
