package dev.kondrashka.ccutf8compat.mixins.client.cc_tweaked;

import static dan200.computercraft.client.render.text.FixedWidthFontRenderer.FONT_HEIGHT;
import static dan200.computercraft.client.render.text.FixedWidthFontRenderer.FONT_WIDTH;
import static dan200.computercraft.shared.media.items.PrintoutData.LINES_PER_PAGE;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;

import dan200.computercraft.client.render.PrintoutRenderer;
import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import dan200.computercraft.core.terminal.Palette;
import dan200.computercraft.core.terminal.TextBuffer;
import dan200.computercraft.core.util.Colour;
import dan200.computercraft.shared.media.items.PrintoutData;

import dev.kondrashka.ccutf8compat.config.CcUtf8CompatConfig;
import dev.kondrashka.ccutf8compat.access.CcUtf8TextBufferAccess;

/**
 * Renders UTF-8 text on printed pages and books with Minecraft's font.
 * <p>
 * CC:Tweaked 1.21.x removed the {@code String[]} overload of {@code drawText};
 * the modern overload accepts {@code List<PrintoutData.Line>}. Both are handled
 * here - the String[] overload is intentionally omitted (it no longer exists
 * upstream).
 */

@Mixin(value = PrintoutRenderer.class, remap = false)
public class PrintoutRendererMixin {

    @Inject(method = "drawText(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IIII[Ldan200/computercraft/core/terminal/TextBuffer;[Ldan200/computercraft/core/terminal/TextBuffer;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ccUtf8$drawTextUtf8(
            PoseStack transform,
            MultiBufferSource bufferSource,
            int x,
            int y,
            int start,
            int light,
            TextBuffer[] text,
            TextBuffer[] colours,
            CallbackInfo ci) {
        if (!CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get()) {
            return;
        }

        for (var line = 0; line < LINES_PER_PAGE; line++) {
            var index = start + line;

            if (index >= text.length || index >= colours.length) {
                break;
            }

            ccUtf8$drawLine(transform, bufferSource, x, y + line * FONT_HEIGHT, light, text[index], colours[index]);
        }

        ci.cancel();
    }

    @Inject(method = "drawText(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IIIILjava/util/List;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ccUtf8$drawLinesUtf8(
            PoseStack transform,
            MultiBufferSource bufferSource,
            int x,
            int y,
            int start,
            int light,
            List<PrintoutData.Line> lines,
            CallbackInfo ci) {
        if (!CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get()) {
            return;
        }

        var text = new TextBuffer[lines.size()];
        var colours = new TextBuffer[lines.size()];

        for (var i = 0; i < lines.size(); i++) {
            text[i] = new TextBuffer(lines.get(i).text());
            colours[i] = new TextBuffer(lines.get(i).foreground());
        }

        for (var line = 0; line < LINES_PER_PAGE; line++) {
            var index = start + line;

            if (index >= text.length || index >= colours.length) {
                break;
            }

            ccUtf8$drawLine(transform, bufferSource, x, y + line * FONT_HEIGHT, light, text[index], colours[index]);
        }

        ci.cancel();
    }

    @Unique
    private static void ccUtf8$drawLine(
            PoseStack transform,
            MultiBufferSource bufferSource,
            int startX,
            int startY,
            int light,
            TextBuffer textLine,
            TextBuffer colourLine) {
        var font = Minecraft.getInstance().font;
        var matrix = transform.last().pose();
        var textAccess = (CcUtf8TextBufferAccess) (Object) textLine;

        for (var x = 0; x < textLine.length(); x++) {
            var codepoint = textAccess.ccUtf8$codePointAt(x);

            if (codepoint <= 0 || codepoint == ' ') {
                continue;
            }

            var text = new String(Character.toChars(codepoint));
            var colourChar = x < colourLine.length() ? colourLine.charAt(x) : '0';
            var textColour = Palette.DEFAULT.getRenderColours(
                    FixedWidthFontRenderer.getColour(colourChar, Colour.BLACK));

            var drawX = startX + x * FONT_WIDTH + Math.max(0.0f, (FONT_WIDTH - font.width(text)) / 2.0f);
            var drawY = startY;

            font.drawInBatch(
                    text,
                    drawX,
                    drawY,
                    textColour,
                    false,
                    matrix,
                    bufferSource,
                    Font.DisplayMode.NORMAL,
                    0,
                    light);
        }
    }
}
