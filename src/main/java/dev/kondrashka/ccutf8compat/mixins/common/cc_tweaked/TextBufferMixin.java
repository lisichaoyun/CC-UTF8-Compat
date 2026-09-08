package dev.kondrashka.ccutf8compat.mixins.common.cc_tweaked;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dan200.computercraft.core.terminal.TextBuffer;

import dev.kondrashka.ccutf8compat.config.CcUtf8CompatConfig;
import dev.kondrashka.ccutf8compat.access.CcUtf8TextBufferAccess;

/**
 * Stores Unicode codepoints alongside CC:Tweaked's legacy text buffer so terminal cells can keep UTF-8 text.
 */

@Mixin(value = TextBuffer.class, remap = false)
public class TextBufferMixin implements CcUtf8TextBufferAccess {

    @Shadow
    @Final
    private char[] text;

    @Unique
    private int[] ccUtf8$codepoints;

    @Inject(method = "<init>(CI)V", at = @At("RETURN"), remap = false)
    private void ccUtf8$initFromChar(char c, int length, CallbackInfo ci) {
        ccUtf8$codepoints = new int[text.length];

        for (var i = 0; i < text.length; i++) {
            ccUtf8$codepoints[i] = c;
        }
    }

    @Inject(method = "<init>(Ljava/lang/String;)V", at = @At("RETURN"), remap = false)
    private void ccUtf8$initFromString(String value, CallbackInfo ci) {
        ccUtf8$codepoints = new int[text.length];

        for (var i = 0; i < text.length; i++) {
            ccUtf8$codepoints[i] = text[i];
        }
    }

    @Unique
    private int[] ccUtf8$getCodepoints() {
        if (ccUtf8$codepoints == null || ccUtf8$codepoints.length != text.length) {
            ccUtf8$codepoints = new int[text.length];

            for (var i = 0; i < text.length; i++) {
                ccUtf8$codepoints[i] = text[i];
            }
        }

        return ccUtf8$codepoints;
    }

    @Unique
    private void ccUtf8$setFallbackChar(int index, int codepoint) {
        text[index] = codepoint >= 0 && codepoint <= Character.MAX_VALUE ? (char) codepoint : '?';
    }

    @Unique
    private void ccUtf8$writeCodepoints(String value, int start) {
        var codepoints = ccUtf8$getCodepoints();
        var source = ccUtf8$coerceCodepoints(value);

        var pos = start;
        start = Math.max(start, 0);

        var end = Math.min(start + source.length, pos + source.length);
        end = Math.min(end, codepoints.length);

        for (var i = start; i < end; i++) {
            var codepoint = source[i - pos];

            codepoints[i] = codepoint;
            ccUtf8$setFallbackChar(i, codepoint);
        }
    }

    /**
     * Defensive UTF-8 decoder: if the incoming String looks like Latin-1
     * (every char fits in a single byte) it might actually be UTF-8 bytes
     * that Cobalt's LuaString decoded byte-by-byte. Try to decode and use
     * that result when valid.
     */
    @Unique
    private static int[] ccUtf8$coerceCodepoints(String value) {
        var raw = value.codePoints().toArray();

        if (raw.length < 2) {
            return raw;
        }

        var allLatin1 = true;
        for (var cp : raw) {
            if (cp > 255) {
                allLatin1 = false;
                break;
            }
        }

        if (!allLatin1) {
            return raw;
        }

        var bytes = new byte[raw.length];
        for (var i = 0; i < raw.length; i++) {
            bytes[i] = (byte) raw[i];
        }

        try {
            var decoded = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return decoded.codePoints().toArray();
        } catch (CharacterCodingException ignored) {
            return raw;
        }
    }

    @Overwrite(remap = false)
    public int length() {
        return text.length;
    }

    @Overwrite(remap = false)
    public void write(String value) {
        write(value, 0);
    }

    @Overwrite(remap = false)
    public void write(String value, int start) {
        if (!CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get()) {
            var pos = start;
            start = Math.max(start, 0);

            var end = Math.min(start + value.length(), pos + value.length());
            end = Math.min(end, text.length);

            for (var i = start; i < end; i++) {
                text[i] = value.charAt(i - pos);
                ccUtf8$getCodepoints()[i] = text[i];
            }

            return;
        }

        ccUtf8$writeCodepoints(value, start);
    }

    @Overwrite(remap = false)
    public void write(ByteBuffer value, int start) {
        var codepoints = ccUtf8$getCodepoints();

        var pos = start;
        var bufferPos = value.position();

        start = Math.max(start, 0);

        var length = value.remaining();
        var end = Math.min(start + length, pos + length);
        end = Math.min(end, codepoints.length);

        for (var i = start; i < end; i++) {
            var codepoint = value.get(bufferPos + i - pos) & 0xFF;

            codepoints[i] = codepoint;
            text[i] = (char) codepoint;
        }
    }

    @Overwrite(remap = false)
    public void write(TextBuffer value) {
        var codepoints = ccUtf8$getCodepoints();
        var access = (CcUtf8TextBufferAccess) (Object) value;

        var end = Math.min(value.length(), codepoints.length);

        for (var i = 0; i < end; i++) {
            var codepoint = access.ccUtf8$codePointAt(i);

            codepoints[i] = codepoint;
            ccUtf8$setFallbackChar(i, codepoint);
        }
    }

    @Overwrite(remap = false)
    public void fill(char c) {
        fill(c, 0, text.length);
    }

    @Overwrite(remap = false)
    public void fill(char c, int start, int end) {
        var codepoints = ccUtf8$getCodepoints();

        start = Math.max(start, 0);
        end = Math.min(end, codepoints.length);

        for (var i = start; i < end; i++) {
            codepoints[i] = c;
            text[i] = c;
        }
    }

    @Overwrite(remap = false)
    public char charAt(int index) {
        if (!CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get()) {
            return text[index];
        }

        var codepoint = ccUtf8$getCodepoints()[index];

        return codepoint >= 0 && codepoint <= Character.MAX_VALUE ? (char) codepoint : '?';
    }

    @Overwrite(remap = false)
    public void setChar(int index, char c) {
        if (index >= 0 && index < text.length) {
            text[index] = c;
            ccUtf8$getCodepoints()[index] = c;
        }
    }

    @Override
    public int ccUtf8$codePointAt(int index) {
        return ccUtf8$getCodepoints()[index];
    }

    @Override
    public void ccUtf8$setCodePoint(int index, int codepoint) {
        if (index >= 0 && index < text.length) {
            ccUtf8$getCodepoints()[index] = codepoint;
            ccUtf8$setFallbackChar(index, codepoint);
        }
    }

    @Overwrite(remap = false)
    public String toString() {
        if (!CcUtf8CompatConfig.ENABLE_CC_UTF8_COMPAT.get()) {
            return new String(text);
        }

        var codepoints = ccUtf8$getCodepoints();

        return new String(codepoints, 0, codepoints.length);
    }
}
