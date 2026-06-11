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
    private static final Map<UUID, Double> nativeIronsMax = new HashMap<>();
    private static int tickCounter = 0;
    
    // === ИНИЦИАЛИЗАЦИЯ МАКСИМУМОВ ===
    public static void initMaxMana(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        int arsMax = getNativeArsMax(player);
        double ironsMax = getNativeIronsMaxMana(player);
        
        nativeArsMax.put(playerId, arsMax);
        nativeIronsMax.put(playerId, ironsMax);
        
        double totalMax = calculateMax(arsMax, ironsMax);
        setIronsMaxMana(player, totalMax);
        
        // Установим текущую ману пропорционально
        double currentIrons = getIronsMana(player);
        if (currentIrons > totalMax) {
            setIronsMana(player, totalMax);
        }
    }
    
    // === ТИК СИНХРОНИЗАЦИИ ===
    public static void tick(ServerPlayer player) {
        tickCounter++;
        
        if (Config.BATCH_UPDATE.get()) {
            if (tickCounter % 10 != 0) return;
        }
        
        UUID playerId = player.getUUID();
        
        double ironsMana = getIronsMana(player);
        double ironsMax = getIronsMaxMana(player);
        int arsMax = nativeArsMax.getOrDefault(playerId, 100);
        
        if (ironsMax <= 0 || arsMax <= 0) return;
        
        double currentArsMana = getArsMana(player);
        double currentArsPercent = arsMax > 0 ? (currentArsMana / arsMax) * 100.0 : 0;
        double ironsPercent = ironsMax > 0 ? (ironsMana / ironsMax) * 100.0 : 0;
        
        Double lastPercent = lastArsPercent.get(playerId);
        Double lastIrons = lastIronsMana.get(playerId);
        
        // === ОТСЛЕЖИВАНИЕ ИЗМЕНЕНИЙ ARS ===
        if (lastPercent != null && currentArsPercent < lastPercent - 0.1) {
            double deltaArsMana = (lastPercent - currentArsPercent) / 100.0 * arsMax;
            double newIronsMana = Math.max(0, ironsMana - deltaArsMana);
            setIronsMana(player, newIronsMana);
            ironsMana = newIronsMana;
            ironsPercent = ironsMax > 0 ? (ironsMana / ironsMax) * 100.0 : 0;
        }
        
        // === ОТСЛЕЖИВАНИЕ ИЗМЕНЕНИЙ IRON'S ===
        if (lastIrons != null && ironsMana < lastIrons - 0.5) {
            syncArsFromPercent(player, ironsPercent, arsMax);
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
        
        // === СИНХРОНИЗАЦИЯ ПО НАПРАВЛЕНИЮ ===
        String direction = Config.SYNC_DIRECTION.get();
        switch (direction) {
            case "both":
                syncArsFromPercent(player, ironsPercent, arsMax);
                break;
            case "ars_to_irons":
                setIronsMana(player, (currentArsPercent / 100.0) * ironsMax);
                break;
            case "irons_to_ars":
                syncArsFromPercent(player, ironsPercent, arsMax);
                break;
        }
        
        lastIronsMana.put(playerId, ironsMana);
        lastArsPercent.put(playerId, ironsPercent);
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
        
        return Math.max(1, total);
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
    private static double getNativeIronsMaxMana(ServerPlayer player) {
        try {
            var attr = player.getAttributes().getInstance(AttributeRegistry.MAX_MANA);
            return attr != null ? attr.getBaseValue() : 200;
        } catch (Exception e) { return 200; }
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
        int arsMax = nativeArsMax.getOrDefault(playerId, 100);
        double ironsMaxNative = nativeIronsMax.getOrDefault(playerId, 200.0);
        double totalMax = getIronsMaxMana(player);
        double ironsMana = getIronsMana(player);
        
        // Расчёт регенерации
        double ironsRegen = player.getAttribute(AttributeRegistry.MANA_REGEN) != null ? 
            player.getAttributeValue(AttributeRegistry.MANA_REGEN) : 0;
        
        source.sendSuccess(() -> 
            net.minecraft.network.chat.Component.literal(
                "§6=== Mana Bridge Info ===\n" +
                "§bОбщий максимум: §f" + String.format("%.0f", totalMax) + "\n" +
                "§bТекущая мана: §f" + String.format("%.0f", ironsMana) + "/" + String.format("%.0f", totalMax) + "\n" +
                "§bРегенерация: §f" + String.format("%.1f", ironsRegen * 100) + "% /тик\n" +
                "§bОт Ars: §f+" + arsMax + " макс (родной)\n" +
                "§bОт Iron's: §f+" + String.format("%.0f", ironsMaxNative) + " макс (родной)\n" +
                "§bМножители: §fArs ×" + Config.K_ARS.get() + ", Iron's ×" + Config.K_IRONS.get()
            ), false
        );
    }
}
