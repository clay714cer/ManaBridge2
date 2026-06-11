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
    private static final Map<UUID, Integer> storedNativeArsMax = new HashMap<>();
    private static final Map<UUID, Double> storedNativeIronsMax = new HashMap<>();
    private static int tickCounter = 0;
    
    // === ИНИЦИАЛИЗАЦИЯ ===
    public static void onPlayerLogin(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        if (!storedNativeArsMax.containsKey(playerId)) {
            storedNativeArsMax.put(playerId, 100);
        }
        if (!storedNativeIronsMax.containsKey(playerId)) {
            storedNativeIronsMax.put(playerId, 100.0);
        }
        
        applyMaxMana(player);
    }
    
    public static void onEquipmentChanged(ServerPlayer player) {
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
        
        // Обновление максимума (раз в 5 сек)
        if (tickCounter % 100 == 0) {
            applyMaxMana(player);
        }
        
        // Синхронизация текущей маны
        if (Config.BATCH_UPDATE.get() && tickCounter % 10 != 0) return;
        
        double ironsMana = getIronsMana(player);
        double ironsMax = getIronsMaxMana(player);
        int arsMax = storedNativeArsMax.getOrDefault(playerId, 100);
        
        if (ironsMax <= 0 || arsMax <= 0) return;
        
        double currentArsMana = getArsMana(player);
        double currentArsPercent = arsMax > 0 ? (currentArsMana / arsMax) * 100.0 : 0;
        double ironsPercent = ironsMax > 0 ? (ironsMana / ironsMax) * 100.0 : 0;
        
        Double lastPercent = lastArsPercent.get(playerId);
        Double lastIrons = lastIronsMana.get(playerId);
        
        // Отслеживание трат Ars
        if (lastPercent != null && currentArsPercent < lastPercent - 0.1) {
            double deltaArsMana = (lastPercent - currentArsPercent) / 100.0 * arsMax;
            double newIronsMana = Math.max(0, ironsMana - deltaArsMana);
            setIronsMana(player, newIronsMana);
            ironsMana = newIronsMana;
            ironsPercent = ironsMax > 0 ? (ironsMana / ironsMax) * 100.0 : 0;
        }
        
        // Отслеживание восстановления Ars
        if (lastPercent != null && currentArsPercent > lastPercent + 0.1) {
            double deltaArsMana = (currentArsPercent - lastPercent) / 100.0 * arsMax;
            double newIronsMana = Math.min(ironsMax, ironsMana + deltaArsMana);
            setIronsMana(player, newIronsMana);
            ironsMana = newIronsMana;
            ironsPercent = ironsMax > 0 ? (ironsMana / ironsMax) * 100.0 : 0;
        }
        
        // Отслеживание Iron's
        if (lastIrons != null && Math.abs(ironsMana - lastIrons) > 0.5) {
            syncArsFromPercent(player, ironsPercent, arsMax);
            currentArsPercent = ironsPercent;
        }
        
        // Синхронизация
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
    
    // === ПРИМЕНЕНИЕ МАКСИМУМА ===
    private static void applyMaxMana(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        int nativeArsMax = storedNativeArsMax.getOrDefault(playerId, 100);
        double nativeIronsMax = storedNativeIronsMax.getOrDefault(playerId, 100.0);
        
        double totalMax = (nativeArsMax * Config.K_ARS.get()) + (nativeIronsMax * Config.K_IRONS.get()) - Config.N_VALUE.get();
        
        double cap = Config.MAX_MANA_CAP.get();
        if (cap > 0 && totalMax > cap) totalMax = cap;
        if (totalMax < 1) totalMax = 1;
        
        setIronsMaxMana(player, totalMax);
        setArsMaxMana(player, (int) totalMax);
        
        double currentIrons = getIronsMana(player);
        double currentArs = getArsMana(player);
        if (currentIrons > totalMax) setIronsMana(player, totalMax);
        if (currentArs > totalMax) setArsMana(player, totalMax);
    }
    
    // === СИНХРОНИЗАЦИЯ ARS ===
    private static void syncArsFromPercent(ServerPlayer player, double percent, int arsMax) {
        double arsValue = (percent / 100.0) * arsMax;
        setArsMana(player, arsValue);
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
        
        double totalMax = getIronsMaxMana(player);
        double ironsMana = getIronsMana(player);
        double arsMana = getArsMana(player);
        
        source.sendSuccess(() -> 
            net.minecraft.network.chat.Component.literal(
                "§6=== Mana Bridge Info ===\n" +
                "§bОбщий максимум: §f" + String.format("%.0f", totalMax) + "\n" +
                "§bIron's: §f" + String.format("%.0f", ironsMana) + "/" + String.format("%.0f", totalMax) + "\n" +
                "§bArs: §f" + String.format("%.0f", arsMana) + "/" + String.format("%.0f", totalMax) + "\n" +
                "§bМножители: §fArs ×" + Config.K_ARS.get() + ", Iron's ×" + Config.K_IRONS.get()
            ), false
        );
    }
}
