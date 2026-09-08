package dev.kondrashka.ccutf8compat.mixins.client.cc_tweaked;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.world.inventory.AbstractContainerMenu;

import dan200.computercraft.client.gui.ClientComputerInput;
import dan200.computercraft.client.network.ClientNetworking;
import dan200.computercraft.shared.network.server.KeyEventServerMessage;

import dev.kondrashka.ccutf8compat.config.CcUtf8CompatConfig;
import dev.kondrashka.ccutf8compat.access.CcUtf8ClientInputAccess;

/**
 * Sends UTF-8 character and paste input without CC:Tweaked's legacy filtering.
 * <p>
 * In CC:Tweaked 1.21.x the client-side input helper was renamed from
 * {@code ClientInputHandler} to {@code ClientComputerInput}, and the
 * character event now accepts a {@code byte} codepoint rather than a
 * {@code char} - we add a bridge that sends the full int codepoint via the
 * existing {@code KeyEventServerMessage.Action.CHAR} channel.
 */

@Mixin(value = ClientComputerInput.class, remap = false)
public class ClientInputHandlerMixin implements CcUtf8ClientInputAccess {

    @Shadow
    @Final
    private AbstractContainerMenu menu;

    @Override
    public void ccUtf8$charTypedCodepoint(int codepoint) {
        if (!CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get()) {
            return;
        }

        ClientNetworking.sendToServer(new KeyEventServerMessage(
                menu,
                KeyEventServerMessage.Action.CHAR,
                codepoint));
    }
}
