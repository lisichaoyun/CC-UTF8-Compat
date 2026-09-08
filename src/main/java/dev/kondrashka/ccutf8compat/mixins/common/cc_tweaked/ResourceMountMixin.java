package dev.kondrashka.ccutf8compat.mixins.common.cc_tweaked;

import java.io.IOException;
import java.io.InputStream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

import dan200.computercraft.impl.AbstractComputerCraftAPI;
import dan200.computercraft.shared.computer.core.ResourceMount;

import dev.kondrashka.ccutf8compat.CcUtf8Compat;
import dev.kondrashka.ccutf8compat.config.CcUtf8CompatConfig;

/**
 * Loads UTF-8 patched CraftOS Lua resources before CC:Tweaked's bundled resources.
 */

@Mixin(value = ResourceMount.class, remap = false)
public class ResourceMountMixin {

    @Shadow
    private String namespace;

    @Shadow
    private String subPath;

    @Shadow
    private ResourceManager manager;

    @Unique
    private static final String ccUtf8$CC_NAMESPACE = "computercraft";

    @Unique
    private static final String ccUtf8$CC_LUA_PATH = "lua";

    @Inject(method = "getFileContents", at = @At("HEAD"), cancellable = true, remap = false)
    private void ccUtf8$getFileContents(String path, @Coerce Object file, CallbackInfoReturnable<byte[]> cir) throws IOException {
        if (!CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get()) {
            return;
        }

        if (!ccUtf8$CC_NAMESPACE.equals(namespace)) {
            return;
        }

        if (!ccUtf8$isLuaPath(subPath)) {
            return;
        }

        var overrideLocation = ccUtf8$overrideLocation(subPath + "/" + path);
        var resource = manager.getResource(overrideLocation).orElse(null);

        if (resource == null) {
            return;
        }

        try (var stream = resource.open()) {
            cir.setReturnValue(stream.readAllBytes());
        }
    }

    @Unique
    private static boolean ccUtf8$isLuaPath(String path) {
        return ccUtf8$CC_LUA_PATH.equals(path) || path.startsWith(ccUtf8$CC_LUA_PATH + "/");
    }

    @Unique
    private static ResourceLocation ccUtf8$overrideLocation(String path) {
        return ResourceLocation.fromNamespaceAndPath(CcUtf8Compat.MOD_ID, "computercraft/" + path);
    }
}

/**
 * Applies UTF-8 patched CraftOS Lua resources to CC:Tweaked API mounts.
 */

@Mixin(value = AbstractComputerCraftAPI.class, remap = false)
class AbstractComputerCraftAPIMixin {

    @Unique
    private static final String ccUtf8$CC_NAMESPACE = "computercraft";

    @Unique
    private static final String ccUtf8$CC_LUA_PATH = "lua";

    @Inject(method = "getResourceFile", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ccUtf8$getResourceFile(
            MinecraftServer server,
            String domain,
            String subPath,
            CallbackInfoReturnable<InputStream> cir) {
        if (!CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get()) {
            return;
        }

        if (!ccUtf8$CC_NAMESPACE.equals(domain)) {
            return;
        }

        if (!ccUtf8$isLuaPath(subPath)) {
            return;
        }

        var overrideLocation = ccUtf8$overrideLocation(subPath);
        var resource = server.getResourceManager().getResource(overrideLocation).orElse(null);

        if (resource == null) {
            return;
        }

        try {
            cir.setReturnValue(resource.open());
        } catch (IOException ignored) {
        }
    }

    @Unique
    private static boolean ccUtf8$isLuaPath(String path) {
        return ccUtf8$CC_LUA_PATH.equals(path) || path.startsWith(ccUtf8$CC_LUA_PATH + "/");
    }

    @Unique
    private static ResourceLocation ccUtf8$overrideLocation(String path) {
        return ResourceLocation.fromNamespaceAndPath(CcUtf8Compat.MOD_ID, "computercraft/" + path);
    }
}
