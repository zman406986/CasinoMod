package data.scripts.casino.shared;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

public final class GachaUI {
    private GachaUI() {}

    public static float[] toGLComponents(Color color) {
        return GLColorUtils.toGLComponents(color);
    }

    public static Color darken(Color c) {
        return GLColorUtils.darken(c);
    }

    public static void renderCircle(float cx, float cy, float radius, float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_POLYGON);
        for (int i = 0; i < 8; i++) {
            float angle = (float)(i * 2 * Math.PI / 8);
            GL11.glVertex2f(cx + (float)Math.cos(angle) * radius, cy + (float)Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }

    public static void renderQuad(float x, float y, float w, float h, float r, float g, float b, float a) {
        GLQuadUtils.renderQuad(x, y, w, h, r, g, b, a);
    }

    public static void renderQuad(float x, float y, float w, float h, Color color, float alphaMult) {
        GLQuadUtils.renderQuad(x, y, w, h, color, alphaMult);
    }

    public static void renderBeveledRect(float x, float y, float w, float h, Color baseColor, float thickness, float alphaMult) {
        GLQuadUtils.renderBeveledRect(x, y, w, h, baseColor, thickness, alphaMult);
    }

    public static void renderBevelBorder(float x, float y, float w, float h, Color frameColor, float thickness, float alphaMult) {
        GLQuadUtils.renderBevelBorder(x, y, w, h, frameColor, thickness, alphaMult);
    }

    public static void renderInnerShadow(float x, float y, float w, float h, float thickness, float alphaMult) {
        GLQuadUtils.renderInnerShadow(x, y, w, h, thickness, alphaMult);
    }
}