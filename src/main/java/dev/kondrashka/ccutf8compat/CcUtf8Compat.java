package dev.kondrashka.ccutf8compat;

import com.mojang.logging.LogUtils;
import dev.kondrashka.ccutf8compat.config.CcUtf8CompatConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(CcUtf8Compat.MOD_ID)
public final class CcUtf8Compat {

    public static final String MOD_ID = "cc_utf8_compat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CcUtf8Compat(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CcUtf8CompatConfig.SPEC);

        LOGGER.info("CC UTF-8 Compat loaded");
    }
}
