package dev.kondrashka.ccutf8compat.mixins.client.cc_tweaked;

import static dan200.computercraft.client.render.text.FixedWidthFontRenderer.FONT_HEIGHT;
import static dan200.computercraft.client.render.text.FixedWidthFontRenderer.FONT_WIDTH;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import dan200.computercraft.client.gui.widgets.TerminalWidget;
import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import dan200.computercraft.core.input.UserComputerInput;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.core.util.Colour;

import dev.kondrashka.ccutf8compat.config.CcUtf8CompatConfig;
import dev.kondrashka.ccutf8compat.access.CcUtf8TextBufferAccess;

/**
 * Adds UTF-8 paste handling and Unicode rendering to CC:Tweaked's terminal widget.
 */

@Mixin(value = TerminalWidget.class, remap = false)
public class TerminalWidgetMixin {

    @Shadow
    @Final
    private UserComputerInput computerInput;

    @Shadow
    @Final
    private Terminal terminal;

    @Shadow
    @Final
    private int innerX;

    @Shadow
    @Final
    private int innerY;

    @Inject(method = "renderWidget", at = @At("TAIL"), remap = false)
    private void ccUtf8$renderUnicodeOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get()) {
            return;
        }

        ccUtf8$renderUnicodeOverlay(graphics);
    }

    @Unique
    private void ccUtf8$renderUnicodeOverlay(GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        var palette = terminal.getPalette();

        for (var y = 0; y < terminal.getHeight(); y++) {
            var textLine = terminal.getLine(y);
            var textColourLine = terminal.getTextColourLine(y);
            var backColourLine = terminal.getBackgroundColourLine(y);
            var textAccess = (CcUtf8TextBufferAccess) (Object) textLine;

            for (var x = 0; x < textLine.length(); x++) {
                var codepoint = textAccess.ccUtf8$codePointAt(x);

                if (codepoint >= 0 && codepoint <= 255) {
                    continue;
                }

                var text = new String(Character.toChars(codepoint));
                var drawX = innerX + x * FONT_WIDTH;
                var drawY = innerY + y * FONT_HEIGHT;

                var backgroundColour = palette.getRenderColours(
                        FixedWidthFontRenderer.getColour(backColourLine.charAt(x), Colour.BLACK));

                var textColour = palette.getRenderColours(
                        FixedWidthFontRenderer.getColour(textColourLine.charAt(x), Colour.WHITE));

                graphics.fill(drawX, drawY, drawX + FONT_WIDTH, drawY + FONT_HEIGHT, backgroundColour);

                var glyphWidth = font.width(text);
                var xOffset = Math.max(0, (FONT_WIDTH - glyphWidth) / 2);

                graphics.drawString(font, text, drawX + xOffset, drawY, textColour, false);
            }
        }
    }

    @Inject(method = "paste", at = @At("HEAD"), cancellable = true, remap = false)
    private void ccUtf8$pasteUtf8(CallbackInfo ci) {
        if (!CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get()) {
            return;
        }

        var clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        var paste = ccUtf8$encodePasteUtf8(clipboard);

        if (paste.remaining() > 0) {
            computerInput.paste(paste);
        }

        ci.cancel();
    }

    @Unique
    private static java.nio.ByteBuffer ccUtf8$encodePasteUtf8(String clipboard) {
        var output = java.nio.ByteBuffer.allocate(32640);
        var iterator = clipboard.codePoints().iterator();

        while (iterator.hasNext()) {
            var codepoint = iterator.nextInt();

            if (codepoint == '\r' || codepoint == '\n' || codepoint == 0) {
                break;
            }

            var bytes = new String(Character.toChars(codepoint)).getBytes(java.nio.charset.StandardCharsets.UTF_8);

            if (bytes.length > output.remaining()) {
                break;
            }

            output.put(bytes);
        }

        output.flip();

        return output.asReadOnlyBuffer();
    }
}
