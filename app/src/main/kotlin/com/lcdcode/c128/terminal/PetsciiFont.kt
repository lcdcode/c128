package com.lcdcode.c128.terminal

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 8x8 glyph atlas covering ASCII 0x20..0x7E.
 *
 * Glyph data is the public-domain font8x8_basic table by Marcel Sondaar
 * (https://github.com/dhepper/font8x8). Each glyph is 8 bytes (one byte per
 * row, top to bottom); bit 0 (LSB) of each byte is the leftmost pixel.
 */
object PetsciiFont {
    const val CELL = 8
    const val FIRST = 0x20
    const val LAST = 0x7E
    const val COUNT = LAST - FIRST + 1

    private var atlas: ImageBitmap? = null

    fun atlas(): ImageBitmap = atlas ?: build().also { atlas = it }

    // 95 entries, ASCII 0x20..0x7E. Each entry is exactly 16 hex characters
    // = 8 row bytes, top to bottom, bit 0 = leftmost pixel.
    private val GLYPH_HEX: Array<String> = arrayOf(
        "0000000000000000", // 0x20 SPACE
        "183C3C1818001800", // 0x21 !
        "3636000000000000", // 0x22 "
        "36367F367F363600", // 0x23 #
        "0C3E031E301F0C00", // 0x24 $
        "006333180C666300", // 0x25 %
        "1C361C6E3B336E00", // 0x26 &
        "0606030000000000", // 0x27 '
        "180C0606060C1800", // 0x28 (
        "060C1818180C0600", // 0x29 )
        "00663CFF3C660000", // 0x2A *
        "000C0C3F0C0C0000", // 0x2B +
        "00000000000C0C06", // 0x2C ,
        "0000003F00000000", // 0x2D -
        "00000000000C0C00", // 0x2E .
        "603018060C030100", // 0x2F /
        "3E63737B6F673E00", // 0x30 0
        "0C0E0C0C0C0C3F00", // 0x31 1
        "1E33301C06333F00", // 0x32 2
        "1E33301C30331E00", // 0x33 3
        "383C36337F307800", // 0x34 4
        "3F031F3030331E00", // 0x35 5
        "1C06031F33331E00", // 0x36 6
        "3F3330180C0C0C00", // 0x37 7
        "1E33331E33331E00", // 0x38 8
        "1E33333E30180E00", // 0x39 9
        "000C0C00000C0C00", // 0x3A :
        "000C0C00000C0C06", // 0x3B ;
        "180C0603060C1800", // 0x3C <
        "00003F00003F0000", // 0x3D =
        "060C1830180C0600", // 0x3E >
        "1E3330180C000C00", // 0x3F ?
        "3E637B7B7B031E00", // 0x40 @
        "0C1E33333F333300", // 0x41 A
        "3F66663E66663F00", // 0x42 B
        "3C66030303663C00", // 0x43 C
        "1F36666666361F00", // 0x44 D
        "7F46161E16467F00", // 0x45 E
        "7F46161E16060F00", // 0x46 F
        "3C66030373667C00", // 0x47 G
        "3333333F33333300", // 0x48 H
        "1E0C0C0C0C0C1E00", // 0x49 I
        "7830303033331E00", // 0x4A J
        "6766361E36666700", // 0x4B K
        "0F06060646667F00", // 0x4C L
        "63777F7F6B636300", // 0x4D M
        "63676F7B73636300", // 0x4E N
        "1C36636363361C00", // 0x4F O
        "3F66663E06060F00", // 0x50 P
        "1E3333333B1E3800", // 0x51 Q
        "3F66663E36666700", // 0x52 R
        "1E33070E38331E00", // 0x53 S
        "3F2D0C0C0C0C1E00", // 0x54 T
        "3333333333333F00", // 0x55 U
        "33333333331E0C00", // 0x56 V
        "6363636B7F776300", // 0x57 W
        "6363361C1C366300", // 0x58 X
        "3333331E0C0C1E00", // 0x59 Y
        "7F6331184C667F00", // 0x5A Z
        "1E06060606061E00", // 0x5B [
        "03060C1830604000", // 0x5C \
        "1E18181818181E00", // 0x5D ]
        "081C366300000000", // 0x5E ^
        "00000000000000FF", // 0x5F _
        "0C0C180000000000", // 0x60 `
        "00001E303E336E00", // 0x61 a
        "0706063E66663B00", // 0x62 b
        "00001E3303331E00", // 0x63 c
        "3830303E33336E00", // 0x64 d
        "00001E333F031E00", // 0x65 e
        "1C36060F06060F00", // 0x66 f
        "00006E33333E301F", // 0x67 g
        "0706366E66666700", // 0x68 h
        "0C000E0C0C0C1E00", // 0x69 i
        "300030303033331E", // 0x6A j
        "070666361E366700", // 0x6B k
        "0E0C0C0C0C0C1E00", // 0x6C l
        "0000337F7F6B6300", // 0x6D m
        "00001F3333333300", // 0x6E n
        "00001E3333331E00", // 0x6F o
        "00003B66663E060F", // 0x70 p
        "00006E33333E3078", // 0x71 q
        "00003B6E66060F00", // 0x72 r
        "00003E031E301F00", // 0x73 s
        "080C3E0C0C2C1800", // 0x74 t
        "0000333333336E00", // 0x75 u
        "00003333331E0C00", // 0x76 v
        "0000636B7F7F3600", // 0x77 w
        "000063361C366300", // 0x78 x
        "00003333333E301F", // 0x79 y
        "00003F190C263F00", // 0x7A z
        "380C0C070C0C3800", // 0x7B {
        "1818180018181800", // 0x7C |
        "070C0C380C0C0700", // 0x7D }
        "6E3B000000000000", // 0x7E ~
    )

    private val FULL: ByteArray by lazy { decode() }

    private fun decode(): ByteArray {
        require(GLYPH_HEX.size == COUNT) {
            "Expected $COUNT glyph entries, got ${GLYPH_HEX.size}"
        }
        val out = ByteArray(COUNT * CELL)
        for (i in 0 until COUNT) {
            val s = GLYPH_HEX[i]
            require(s.length == 16) {
                "Glyph $i (ASCII 0x${(FIRST + i).toString(16)}) is ${s.length} chars, expected 16"
            }
            for (k in 0 until 8) {
                out[i * CELL + k] = (
                    (Character.digit(s[k * 2], 16) shl 4) or
                        Character.digit(s[k * 2 + 1], 16)
                    ).toByte()
            }
        }
        return out
    }

    private fun build(): ImageBitmap {
        val out = Bitmap.createBitmap(CELL * COUNT, CELL, Bitmap.Config.ARGB_8888)
        for (i in 0 until COUNT) {
            for (y in 0 until CELL) {
                val rowByte = FULL[i * CELL + y].toInt() and 0xFF
                for (x in 0 until CELL) {
                    val on = (rowByte shr x) and 1 == 1
                    out.setPixel(i * CELL + x, y, if (on) Color.WHITE else Color.TRANSPARENT)
                }
            }
        }
        return out.asImageBitmap()
    }
}
