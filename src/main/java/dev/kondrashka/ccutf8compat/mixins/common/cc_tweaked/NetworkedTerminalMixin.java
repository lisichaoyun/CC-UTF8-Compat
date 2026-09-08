package dev.kondrashka.ccutf8compat.mixins.common.cc_tweaked;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dan200.computercraft.core.terminal.Palette;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.core.util.Colour;
import dan200.computercraft.shared.computer.terminal.NetworkedTerminal;
import dan200.computercraft.shared.computer.terminal.TerminalState;

import dev.kondrashka.ccutf8compat.config.CcUtf8CompatConfig;
import dev.kondrashka.ccutf8compat.access.CcUtf8TerminalStateAccess;
import dev.kondrashka.ccutf8compat.access.CcUtf8TextBufferAccess;

/**
 * Synchronizes UTF-8 terminal data through CC:Tweaked's networked terminal updates.
 * <p>
 * The original {@link NetworkedTerminal#write()} creates a vanilla {@link TerminalState};
 * we use {@code @Inject} at RETURN to grab that freshly constructed state and attach
 * UTF-8 codepoint sidecar data via {@link CcUtf8TerminalStateAccess}. The actual byte
 * serialisation is handled by {@code TerminalStateMixin#write} at TAIL.
 * <p>
 * Sidecar data is stored directly on the returned TerminalState instance (no static
 * bridge), so concurrent writes on different terminals cannot interfere.
 */

@Mixin(value = NetworkedTerminal.class, remap = false)
public class NetworkedTerminalMixin {

    @Unique
    private static final String ccUtf8$BASE_16 = "0123456789abcdef";

    @Inject(method = "write", at = @At("RETURN"), remap = false)
    private void ccUtf8$writeUtf8State(CallbackInfoReturnable<TerminalState> cir) {
        if (!CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get()) {
            return;
        }

        var state = cir.getReturnValue();
        if (state == null) {
            return;
        }

        var terminal = (NetworkedTerminal) (Object) this;
        var width = terminal.getWidth();
        var height = terminal.getHeight();
        var palette = terminal.getPalette();

        var textContents = new int[width * height];
        var colours = new byte[width * height];
        var paletteBytes = new byte[Palette.PALETTE_SIZE * 3];

        var textIdx = 0;
        var colourIdx = 0;
        var paletteIdx = 0;

        for (var y = 0; y < height; y++) {
            var textLine = terminal.getLine(y);
            var textColourLine = terminal.getTextColourLine(y);
            var backColourLine = terminal.getBackgroundColourLine(y);

            var access = (CcUtf8TextBufferAccess) (Object) textLine;

            for (var x = 0; x < width; x++) {
                textContents[textIdx++] = access.ccUtf8$codePointAt(x);
            }

            for (var x = 0; x < width; x++) {
                colours[colourIdx++] = (byte) (Terminal.getColour(backColourLine.charAt(x), Colour.BLACK) << 4 |
                        Terminal.getColour(textColourLine.charAt(x), Colour.WHITE));
            }
        }

        for (var i = 0; i < Palette.PALETTE_SIZE; i++) {
            for (var channel : palette.getColour(i)) {
                paletteBytes[paletteIdx++] = (byte) ((int) (channel * 0xFF) & 0xFF);
            }
        }

        ((CcUtf8TerminalStateAccess) state).ccUtf8$setUtf8Data(textContents, colours, paletteBytes);
    }

    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void ccUtf8$readUtf8State(TerminalState state, CallbackInfo ci) {
        if (!CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get()) {
            return;
        }

        var utf8State = (CcUtf8TerminalStateAccess) state;
        var textContents = utf8State.ccUtf8$getUtf8Text();
        var colours = utf8State.ccUtf8$getUtf8Colours();
        var paletteBytes = utf8State.ccUtf8$getUtf8Palette();

        if (textContents == null || colours == null || paletteBytes == null) {
            return;
        }

        var terminal = (NetworkedTerminal) (Object) this;

        var width = terminal.getWidth();
        var height = terminal.getHeight();

        var textIdx = 0;
        var colourIdx = 0;
        var paletteIdx = 0;

        for (var y = 0; y < height; y++) {
            var textLine = terminal.getLine(y);
            var textColourLine = terminal.getTextColourLine(y);
            var backColourLine = terminal.getBackgroundColourLine(y);

            var textAccess = (CcUtf8TextBufferAccess) (Object) textLine;

            for (var x = 0; x < width; x++) {
                if (textIdx < textContents.length) {
                    textAccess.ccUtf8$setCodePoint(x, textContents[textIdx++]);
                }
            }

            for (var x = 0; x < width; x++) {
                if (colourIdx < colours.length) {
                    var packedColour = colours[colourIdx++];

                    backColourLine.setChar(x, ccUtf8$BASE_16.charAt((packedColour >> 4) & 0xF));
                    textColourLine.setChar(x, ccUtf8$BASE_16.charAt(packedColour & 0xF));
                }
            }
        }

        var palette = terminal.getPalette();

        for (var i = 0; i < Palette.PALETTE_SIZE; i++) {
            var r = (paletteBytes[paletteIdx++] & 0xFF) / 255.0;
            var g = (paletteBytes[paletteIdx++] & 0xFF) / 255.0;
            var b = (paletteBytes[paletteIdx++] & 0xFF) / 255.0;

            palette.setColour(i, r, g, b);
        }
    }
}
