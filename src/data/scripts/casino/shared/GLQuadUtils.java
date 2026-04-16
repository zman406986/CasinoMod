package data.scripts.casino.shared;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

import static data.scripts.casino.shared.GLColorUtils.*;

public final class GLQuadUtils {
    private GLQuadUtils() {}

    public static void renderQuad(float x, float y, float w, float h, float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    public static void renderQuad(float x, float y, float w, float h, Color color, float alphaMult) {
        float[] c = toGLComponents(color);
        renderQuad(x, y, w, h, c[0], c[1], c[2], alphaMult);
    }

    public static void renderBeveledRect(float x, float y, float w, float h, Color baseColor, float thickness, float alphaMult) {
        float[] bc = toGLComponents(baseColor);
        float[] lc = toGLComponents(brighten(baseColor));
        float[] dc = toGLComponents(darken(baseColor));

        GL11.glColor4f(lc[0], lc[1], lc[2], alphaMult);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y + h - thickness);
        GL11.glVertex2f(x + w, y + h - thickness);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();

        GL11.glColor4f(dc[0], dc[1], dc[2], alphaMult);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + thickness, y);
        GL11.glVertex2f(x + thickness, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();

        GL11.glColor4f(bc[0], bc[1], bc[2], alphaMult);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x + thickness, y + thickness);
        GL11.glVertex2f(x + w - thickness, y + thickness);
        GL11.glVertex2f(x + w - thickness, y + h - thickness);
        GL11.glVertex2f(x + thickness, y + h - thickness);
        GL11.glEnd();

        GL11.glColor4f(lc[0], lc[1], lc[2], alphaMult);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y + h - thickness);
        GL11.glVertex2f(x + thickness, y + h - thickness);
        GL11.glVertex2f(x + thickness, y);
        GL11.glVertex2f(x, y);
        GL11.glEnd();

        GL11.glColor4f(dc[0], dc[1], dc[2], alphaMult);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x + w - thickness, y + h - thickness);
        GL11.glVertex2f(x + w, y + h - thickness);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w - thickness, y);
        GL11.glEnd();
    }

    public static void renderBevelBorder(float x, float y, float w, float h, Color frameColor, float thickness, float alphaMult) {
        float[] lc = toGLComponents(brighten(frameColor));
        float[] dc = toGLComponents(darken(frameColor));

        GL11.glColor4f(lc[0], lc[1], lc[2], alphaMult);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y + h - thickness);
        GL11.glVertex2f(x + w, y + h - thickness);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + thickness, y);
        GL11.glVertex2f(x + thickness, y + thickness);
        GL11.glVertex2f(x, y + thickness);
        GL11.glEnd();

        GL11.glColor4f(dc[0], dc[1], dc[2], alphaMult);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + thickness, y);
        GL11.glVertex2f(x + thickness, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w - thickness, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w - thickness, y + h);
        GL11.glEnd();
    }

    public static void renderInnerShadow(float x, float y, float w, float h, float thickness, float alphaMult) {
        float shAlpha = alphaMult * 0.4f;
        float[] sc = new float[] { 0.1f, 0.1f, 0.15f };

        GL11.glColor4f(sc[0], sc[1], sc[2], shAlpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y + h - thickness);
        GL11.glVertex2f(x, y + h - thickness);
        GL11.glEnd();

        GL11.glColor4f(sc[0], sc[1], sc[2], shAlpha * 0.6f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + thickness, y);
        GL11.glVertex2f(x + thickness, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();

        GL11.glColor4f(sc[0], sc[1], sc[2], shAlpha * 0.4f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x + w - thickness, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w - thickness, y + h);
        GL11.glEnd();
    }
}