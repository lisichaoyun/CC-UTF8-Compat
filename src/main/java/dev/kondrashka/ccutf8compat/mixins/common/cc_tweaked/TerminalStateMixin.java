package dev.kondrashka.ccutf8compat.mixins.common.cc_tweaked;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.FriendlyByteBuf;

import dan200.computercraft.shared.computer.terminal.TerminalState;

import dev.kondrashka.ccutf8compat.config.CcUtf8CompatConfig;
import dev.kondrashka.ccutf8compat.access.CcUtf8TerminalStateAccess;

/**
 * Saves and restores UTF-8 terminal data in terminal state packets.
 * <p>
 * Wire format on top of vanilla {@code TerminalState}:
 * <pre>
 *   [vanilla TerminalState bytes]
 *   [int ccUtf8$UTF8_MARKER = 0x54464755]   -- "UGFT" little-endian
 *   [varint text length]
 *   [varint codepoint] * text length
 *   [byte[] colours]
 *   [byte[] palette]
 * </pre>
 * <p>
 * The marker lets us detect whether a {@code TerminalState} carries our UTF-8
 * sidecar; vanilla terminals will simply skip it.
 * <p>
 * The sidecar lives directly on the {@code TerminalState} instance (no static
 * bridge) so concurrent writes on different terminals can't stomp each other.
 */

@Mixin(value = TerminalState.class, remap = false)
public class TerminalStateMixin implements CcUtf8TerminalStateAccess {

    @Unique
    private static final int ccUtf8$UTF8_MARKER = 0x54464755;

    @Unique
    private int[] ccUtf8$utf8Text;

    @Unique
    private byte[] ccUtf8$utf8Colours;

    @Unique
    private byte[] ccUtf8$utf8Palette;

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("RETURN"), remap = false)
    private void ccUtf8$readUtf8Data(FriendlyByteBuf buf, CallbackInfo ci) {
        if (!CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get()) {
            return;
        }

        if (buf.readableBytes() < Integer.BYTES) {
            return;
        }

        var readerIndex = buf.readerIndex();

        if (buf.readInt() != ccUtf8$UTF8_MARKER) {
            buf.readerIndex(readerIndex);
            return;
        }

        var textLength = buf.readVarInt();
        var text = new int[textLength];

        for (var i = 0; i < textLength; i++) {
            text[i] = buf.readVarInt();
        }

        ccUtf8$utf8Text = text;
        ccUtf8$utf8Colours = buf.readByteArray();
        ccUtf8$utf8Palette = buf.readByteArray();
    }

    @Inject(method = "write", at = @At("TAIL"), remap = false)
    private void ccUtf8$writeUtf8Data(FriendlyByteBuf buf, CallbackInfo ci) {
        if (!CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get()) {
            return;
        }

        var codepoints = ccUtf8$utf8Text;
        var colours = ccUtf8$utf8Colours;
        var palette = ccUtf8$utf8Palette;

        if (codepoints == null || colours == null || palette == null) {
            return;
        }

        buf.writeInt(ccUtf8$UTF8_MARKER);

        buf.writeVarInt(codepoints.length);
        for (var codepoint : codepoints) {
            buf.writeVarInt(codepoint);
        }

        buf.writeByteArray(colours);
        buf.writeByteArray(palette);
    }

    @Override
    public int[] ccUtf8$getUtf8Text() {
        return ccUtf8$utf8Text;
    }

    @Override
    public byte[] ccUtf8$getUtf8Colours() {
        return ccUtf8$utf8Colours;
    }

    @Override
    public byte[] ccUtf8$getUtf8Palette() {
        return ccUtf8$utf8Palette;
    }

    @Override
    public void ccUtf8$setUtf8Data(int[] text, byte[] colours, byte[] palette) {
        ccUtf8$utf8Text = text;
        ccUtf8$utf8Colours = colours;
        ccUtf8$utf8Palette = palette;
    }
}
