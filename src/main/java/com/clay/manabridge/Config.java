package com.clay.manabridge.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec SPEC;
    
    public static final ModConfigSpec.ConfigValue<Double> K_ARS;
    public static final ModConfigSpec.ConfigValue<Double> K_IRONS;
    public static final ModConfigSpec.ConfigValue<Double> N_VALUE;
    public static final ModConfigSpec.ConfigValue<String> DISPLAY_MODE;
    public static final ModConfigSpec.ConfigValue<String> SYNC_DIRECTION;
    public static final ModConfigSpec.ConfigValue<Boolean> BATCH_UPDATE;
    public static final ModConfigSpec.ConfigValue<Boolean> UPDATE_ON_CHANGE_ONLY;
    public static final ModConfigSpec.ConfigValue<Double> MAX_MANA_CAP;
    public static final ModConfigSpec.ConfigValue<String> PASSIVE_REGEN_MODE;
    public static final ModConfigSpec.ConfigValue<Double> COST_MULTIPLIER_ARS;
    public static final ModConfigSpec.ConfigValue<Double> COST_MULTIPLIER_IRONS;
    
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        
        builder.comment("=== Mana Bridge Configuration ===").push("main");
        
        K_ARS = builder
            .comment("Multiplier for Ars Nouveau mana in formula")
            .defineInRange("k_ars", 1.0, 0.0, 1000.0);
        
        K_IRONS = builder
            .comment("Multiplier for Iron's Spells mana in formula")
            .defineInRange("k_irons", 1.0, 0.0, 1000.0);
        
        N_VALUE = builder
            .comment("Number subtracted from total")
            .defineInRange("n_value", 0.0, -1000000.0, 1000000.0);
        
        DISPLAY_MODE = builder
            .comment("Which bar to display: 'irons', 'ars', 'both', 'shared'")
            .define("display_mode", "both");
        
        MAX_MANA_CAP = builder
            .comment("Maximum mana (0 = unlimited)")
            .defineInRange("max_mana_cap", 0.0, 0.0, 1000000.0);
        
        SYNC_DIRECTION = builder
            .comment("Sync direction: 'both', 'ars_to_irons', 'irons_to_ars'")
            .define("sync_direction", "both");
        
        BATCH_UPDATE = builder
            .comment("true = update mana less frequently for optimization")
            .define("batch_update", false);
        
        UPDATE_ON_CHANGE_ONLY = builder
            .comment("true = sync only when mana changes")
            .define("update_on_change_only", true);
        
        PASSIVE_REGEN_MODE = builder
            .comment("Passive regen mode: 'highest', 'average', 'sum', 'none'")
            .define("passive_regen_mode", "highest");
        
        COST_MULTIPLIER_ARS = builder
            .comment("Cost multiplier for Ars Nouveau spells (1.0 = default)")
            .defineInRange("cost_multiplier_ars", 1.0, 0.0, 100.0);
        
        COST_MULTIPLIER_IRONS = builder
            .comment("Cost multiplier for Iron's Spells spells (1.0 = default)")
            .defineInRange("cost_multiplier_irons", 1.0, 0.0, 100.0);
        
        builder.pop();
        SPEC = builder.build();
    }
}
