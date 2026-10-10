package io.github.arstoienn.argus;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.lwjgl.opengl.GL11;

/**
 * The status text and the mouse pointer on the second window.
 *
 * Both are drawn a cell at a time as solid squares, so neither needs a shader or a texture. On
 * OpenGL each cell is a scissor rectangle cleared to the current colour; {@link VulkanWindow}
 * collects the same cells and clears them its own way. Coordinates are GL's, origin bottom left.
 */
final class BitmapText {
    /** Fills square cells with the colour last set. */
    interface Painter {
        void color(float r, float g, float b);

        void cell(int x, int y, int size);
    }

    private static final Painter GL = new Painter() {
        @Override
        public void color(float r, float g, float b) {
            GL11.glClearColor(r, g, b, 1.0F);
        }

        @Override
        public void cell(int x, int y, int size) {
            GL11.glScissor(x, y, size, size);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
    };

    private static final Map<Character, String[]> FONT = new HashMap<>();

    /** The classic arrow. X is the outline, O the fill, the tip at the top left. */
    private static final String[] CURSOR = {
        "X..........",
        "XX.........",
        "XOX........",
        "XOOX.......",
        "XOOOX......",
        "XOOOOX.....",
        "XOOOOOX....",
        "XOOOOOOX...",
        "XOOOOOOOX..",
        "XOOOOOOOOX.",
        "XOOOOOXXXX.",
        "XOOXOOX....",
        "XXX.XOOX...",
        "....XOOX...",
        ".....XXX..."
    };

    private BitmapText() {
    }

    /** Draws the pointer in the current GL context. */
    static void drawCursor(int x, int y, int cell) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        paintCursor(GL, x, y, cell);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /** Draws the status lines in the current GL context. */
    static void drawStatus(String text, int width, int height) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        paintStatus(GL, text, width, height);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /** Paints the pointer with its tip at (x, y), outline first so it shows on any background. */
    static void paintCursor(Painter painter, int x, int y, int cell) {
        paintCursorLayer(painter, x, y, cell, 'X', 0.04F, 0.04F, 0.06F);
        paintCursorLayer(painter, x, y, cell, 'O', 0.97F, 0.98F, 1.0F);
    }

    private static void paintCursorLayer(Painter painter, int x, int y, int cell, char part, float r, float g, float b) {
        painter.color(r, g, b);
        for (int row = 0; row < CURSOR.length; row++) {
            String line = CURSOR[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) == part) {
                    painter.cell(x + col * cell, y - (row + 1) * cell, cell);
                }
            }
        }
    }

    /** Paints the status lines in the bottom left corner, with a drop shadow, scaled to the window. */
    static void paintStatus(Painter painter, String text, int width, int height) {
        if (text.isEmpty()) {
            return;
        }
        String[] lines = text.toUpperCase(Locale.ROOT).split("\n");
        int cell = Math.max(1, Math.round(height * 0.002F));
        int lineHeight = 7 * cell + 2 * cell;
        int left = Math.max(cell, Math.round(width * 0.012F));
        int bottom = Math.round(height * 0.03F);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[lines.length - 1 - i];
            int y = bottom + i * lineHeight;
            paintLine(painter, line, left + cell, y - cell, cell, 0.05F, 0.06F, 0.07F);
            paintLine(painter, line, left, y, cell, 0.96F, 0.98F, 0.98F);
        }
    }

    private static void paintLine(Painter painter, String line, int x, int y, int cell, float r, float g, float b) {
        painter.color(r, g, b);
        for (int i = 0; i < line.length(); i++) {
            String[] glyph = FONT.get(line.charAt(i));
            if (glyph == null) {
                continue;
            }
            int glyphX = x + i * 6 * cell;
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < 5; col++) {
                    if (glyph[row].charAt(col) == '1') {
                        painter.cell(glyphX + col * cell, y + (6 - row) * cell, cell);
                    }
                }
            }
        }
    }

    private static void glyph(char c, String... rows) {
        FONT.put(c, rows);
    }

    static {
        glyph(' ', "00000", "00000", "00000", "00000", "00000", "00000", "00000");
        glyph('A', "01110", "10001", "10001", "11111", "10001", "10001", "10001");
        glyph('B', "11110", "10001", "11110", "10001", "10001", "10001", "11110");
        glyph('C', "01111", "10000", "10000", "10000", "10000", "10000", "01111");
        glyph('D', "11110", "10001", "10001", "10001", "10001", "10001", "11110");
        glyph('E', "11111", "10000", "11110", "10000", "10000", "10000", "11111");
        glyph('F', "11111", "10000", "11110", "10000", "10000", "10000", "10000");
        glyph('G', "01111", "10000", "10000", "10011", "10001", "10001", "01111");
        glyph('H', "10001", "10001", "11111", "10001", "10001", "10001", "10001");
        glyph('I', "11111", "00100", "00100", "00100", "00100", "00100", "11111");
        glyph('J', "00111", "00010", "00010", "00010", "00010", "10010", "01100");
        glyph('K', "10001", "10010", "10100", "11000", "10100", "10010", "10001");
        glyph('L', "10000", "10000", "10000", "10000", "10000", "10000", "11111");
        glyph('M', "10001", "11011", "10101", "10101", "10001", "10001", "10001");
        glyph('N', "10001", "11001", "10101", "10011", "10001", "10001", "10001");
        glyph('O', "01110", "10001", "10001", "10001", "10001", "10001", "01110");
        glyph('P', "11110", "10001", "10001", "11110", "10000", "10000", "10000");
        glyph('Q', "01110", "10001", "10001", "10001", "10101", "10010", "01101");
        glyph('R', "11110", "10001", "10001", "11110", "10100", "10010", "10001");
        glyph('S', "01111", "10000", "10000", "01110", "00001", "00001", "11110");
        glyph('T', "11111", "00100", "00100", "00100", "00100", "00100", "00100");
        glyph('U', "10001", "10001", "10001", "10001", "10001", "10001", "01110");
        glyph('V', "10001", "10001", "10001", "10001", "10001", "01010", "00100");
        glyph('W', "10001", "10001", "10001", "10101", "10101", "11011", "10001");
        glyph('X', "10001", "10001", "01010", "00100", "01010", "10001", "10001");
        glyph('Y', "10001", "10001", "01010", "00100", "00100", "00100", "00100");
        glyph('Z', "11111", "00001", "00010", "00100", "01000", "10000", "11111");
        glyph('0', "01110", "10011", "10101", "10101", "11001", "10001", "01110");
        glyph('1', "00100", "01100", "00100", "00100", "00100", "00100", "01110");
        glyph('2', "01110", "10001", "00001", "00010", "00100", "01000", "11111");
        glyph('3', "11111", "00010", "00100", "00010", "00001", "10001", "01110");
        glyph('4', "00010", "00110", "01010", "10010", "11111", "00010", "00010");
        glyph('5', "11111", "10000", "11110", "00001", "00001", "10001", "01110");
        glyph('6', "00110", "01000", "10000", "11110", "10001", "10001", "01110");
        glyph('7', "11111", "00001", "00010", "00100", "01000", "01000", "01000");
        glyph('8', "01110", "10001", "10001", "01110", "10001", "10001", "01110");
        glyph('9', "01110", "10001", "10001", "01111", "00001", "00010", "01100");
        glyph(':', "00000", "00100", "00100", "00000", "00100", "00100", "00000");
        glyph('(', "00010", "00100", "01000", "01000", "01000", "00100", "00010");
        glyph(')', "01000", "00100", "00010", "00010", "00010", "00100", "01000");
        glyph('_', "00000", "00000", "00000", "00000", "00000", "00000", "11111");
        glyph('-', "00000", "00000", "00000", "01110", "00000", "00000", "00000");
    }
}
