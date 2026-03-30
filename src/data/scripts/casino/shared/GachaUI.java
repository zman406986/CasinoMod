package data.scripts.casino.shared;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.util.Misc;

public final class GachaUI {
    private GachaUI() {}

    public static final void drawStarShape(float x, float y, float width, float height, Color color, float alphaMult) {
        float halfW = width / 2f;
        float halfH = height / 2f;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alphaMult);

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);

        GL11.glVertex2f(x, y);

        GL11.glVertex2f(x, y - halfH);
        GL11.glVertex2f(x + halfW * 0.3f, y - halfH * 0.3f);
        GL11.glVertex2f(x + halfW, y);
        GL11.glVertex2f(x + halfW * 0.3f, y + halfH * 0.3f);
        GL11.glVertex2f(x, y + halfH);
        GL11.glVertex2f(x - halfW * 0.3f, y + halfH * 0.3f);
        GL11.glVertex2f(x - halfW, y);
        GL11.glVertex2f(x - halfW * 0.3f, y - halfH * 0.3f);
        GL11.glVertex2f(x, y - halfH);

        GL11.glEnd();

        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    public static final void drawStarShapeBorder(float x, float y, float width, float height, Color color, float alphaMult) {
        float halfW = width / 2f;
        float halfH = height / 2f;
        float borderThickness = 2f;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alphaMult);

        GL11.glBegin(GL11.GL_QUADS);

        GL11.glVertex2f(x - borderThickness/2, y - halfH);
        GL11.glVertex2f(x + borderThickness/2, y - halfH);
        GL11.glVertex2f(x + borderThickness/2, y - halfH * 0.3f);
        GL11.glVertex2f(x - borderThickness/2, y - halfH * 0.3f);

        GL11.glVertex2f(x - borderThickness/2, y + halfH * 0.3f);
        GL11.glVertex2f(x + borderThickness/2, y + halfH * 0.3f);
        GL11.glVertex2f(x + borderThickness/2, y + halfH);
        GL11.glVertex2f(x - borderThickness/2, y + halfH);

        GL11.glVertex2f(x - halfW, y - borderThickness/2);
        GL11.glVertex2f(x - halfW * 0.3f, y - borderThickness/2);
        GL11.glVertex2f(x - halfW * 0.3f, y + borderThickness/2);
        GL11.glVertex2f(x - halfW, y + borderThickness/2);

        GL11.glVertex2f(x + halfW * 0.3f, y - borderThickness/2);
        GL11.glVertex2f(x + halfW, y - borderThickness/2);
        GL11.glVertex2f(x + halfW, y + borderThickness/2);
        GL11.glVertex2f(x + halfW * 0.3f, y + borderThickness/2);

        GL11.glEnd();

        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    public static final void drawRatingStar(float x, float y, float size, Color color, float alphaMult) {
        float halfSize = size / 2f;
        float quarterSize = size / 4f;

        Misc.renderQuad(x - quarterSize, y - halfSize, quarterSize * 2, size, color, alphaMult);
        Misc.renderQuad(x - halfSize, y - quarterSize, size, quarterSize * 2, color, alphaMult);
    }
}