package dev.kondrashka.ccutf8compat.mixins.client.cc_tweaked;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import dan200.computercraft.client.render.text.FixedWidthFontRenderer.QuadEmitter;
import dan200.computercraft.core.terminal.Palette;
import dan200.computercraft.core.terminal.TextBuffer;
import dan200.computercraft.core.util.Colour;

import dev.kondrashka.ccutf8compat.config.CcUtf8CompatConfig;
import dev.kondrashka.ccutf8compat.access.CcUtf8TextBufferAccess;

/**
 * Prevents CC:T's {@code drawString} from substituting high codepoints with
 * the {@code '?'} placeholder. The legacy font cannot render those codepoints,
 * so the UTF-8 overlay is responsible for them. Painting a {@code '?'} first
 * would clobber the overlay's glyphs in the render batch order.
 */

@Mixin(value = FixedWidthFontRenderer.class, remap = false)
public class FixedWidthFontRendererMixin {

    @Shadow
    public static int FONT_HEIGHT;

    @Shadow
    public static int FONT_WIDTH;

    @Shadow
    private static float WIDTH;

    @Shadow
    public static int getColour(char index, Colour def) {
        throw new AssertionError();
    }

    @Shadow
    private static void quad(QuadEmitter emitter, float x, float y, float x2, float y2, float z, int colour, float u1, float v1, float u2, float v2, int light) {
        throw new AssertionError();
    }

    @Inject(method = "drawChar", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ccUtf8$skipNonAsciiGlyph(
            QuadEmitter emitter, float x, float y, int index, int colour, int light,
            CallbackInfo ci) {
        if (index > 255) {
            ci.cancel();
        }
    }

    @Overwrite(remap = false)
    public static void drawString(QuadEmitter emitter, float x, float y, TextBuffer text, TextBuffer textColour, Palette palette, int light) {
        var enabled = CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get();
        var textAccess = (CcUtf8TextBufferAccess) (Object) text;

        for (var i = 0; i < text.length(); i++) {
            var colour = palette.getRenderColours(getColour(textColour.charAt(i), Colour.BLACK));
            var codepoint = enabled ? textAccess.ccUtf8$codePointAt(i) : text.charAt(i);

            if (codepoint > 255) {
                continue;
            }

            var index = (char) codepoint;
            drawChar(emitter, x + i * FONT_WIDTH, y, index, colour, light);
        }
    }

    @Overwrite(remap = false)
    private static void drawChar(QuadEmitter emitter, float x, float y, int index, int colour, int light) {
        if (index == '\0' || index == ' ') return;

        var column = index % 16;
        var row = index / 16;

        var xStart = 1 + column * (FONT_WIDTH + 2);
        var yStart = 1 + row * (FONT_HEIGHT + 2);

        quad(
            emitter, x, y, x + FONT_WIDTH, y + FONT_HEIGHT, 0, colour,
            xStart / WIDTH, yStart / WIDTH,
            (xStart + FONT_WIDTH) / WIDTH, (yStart + FONT_HEIGHT) / WIDTH,
            light
        );
    }
}
