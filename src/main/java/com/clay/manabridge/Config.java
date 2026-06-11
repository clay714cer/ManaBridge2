package com.clay.manabridge.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec SPEC;
    
    // Коэффициенты для максимума
    public static final ModConfigSpec.ConfigValue<Double> K_ARS_MAX;
    public static final ModConfigSpec.ConfigValue<Double> K_IRONS_MAX;
    
    // Коэффициенты для текущей маны
    public static final ModConfigSpec.ConfigValue<Double> K_ARS_CURRENT;
    public static final ModConfigSpec.ConfigValue<Double> K_IRONS_CURRENT;
    
    // Общие
    public static final ModConfigSpec.ConfigValue<Double> MAX_MANA_CAP;
    public static final ModConfigSpec.ConfigValue<String> SYNC_DIRECTION;
    public static final ModConfigSpec.ConfigValue<Boolean> UPDATE_ON_CHANGE_ONLY;
    public static final ModConfigSpec.ConfigValue<Integer> CURRENT_MANA_SYNC_INTERVAL;
    
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        
        builder.comment("=== Mana Bridge Configuration ===").push("main");
        
        builder.comment("--- Maximum Mana Coefficients ---").push("max_mana");
        K_ARS_MAX = builder
            .comment("Multiplier for Ars Nouveau max mana in total max formula")
            .defineInRange("k_ars_max", 1.0, 0.0, 1000.0);
        K_IRONS_MAX = builder
            .comment("Multiplier for Iron's Spells max mana in total max formula")
            .defineInRange("k_irons_max", 1.0, 0.0, 1000.0);
        builder.pop();
        
        builder.comment("--- Current Mana Coefficients ---").push("current_mana");
        K_ARS_CURRENT = builder
            .comment("Multiplier for Ars Nouveau current mana in shared pool")
            .defineInRange("k_ars_current", 1.0, 0.0, 1000.0);
        K_IRONS_CURRENT = builder
            .comment("Multiplier for Iron's Spells current mana in shared pool")
            .defineInRange("k_irons_current", 1.0, 0.0, 1000.0);
        builder.pop();
        
        builder.comment("--- General Settings ---").push("general");
        MAX_MANA_CAP = builder
            .comment("Hard cap for total mana (0 = unlimited)")
            .defineInRange("max_mana_cap", 0.0, 0.0, 1000000.0);
        SYNC_DIRECTION = builder
            .comment("Sync direction: 'both', 'ars_to_irons', 'irons_to_ars'")
            .define("sync_direction", "both");
        UPDATE_ON_CHANGE_ONLY = builder
            .comment("Only sync when mana changes by more than 0.5")
            .define("update_on_change_only", true);
        CURRENT_MANA_SYNC_INTERVAL = builder
            .comment("How often to sync current mana (in ticks). 0 = every tick")
            .defineInRange("current_mana_sync_interval", 1, 0, 100);
        builder.pop();
        
        builder.pop();
        SPEC = builder.build();
    }
}
