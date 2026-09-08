package dev.kondrashka.ccutf8compat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CcUtf8CompatConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_CC_UTF8_COMPAT;

    static {
        var builder = new ModConfigSpec.Builder();

        ENABLE_CC_UTF8_COMPAT = builder
                .comment("Enable UTF-8 compatibility patches for CC:Tweaked.")
                .define("ccUtf8Compat", true);

        SPEC = builder.build();
    }

    private CcUtf8CompatConfig() {
    }
}