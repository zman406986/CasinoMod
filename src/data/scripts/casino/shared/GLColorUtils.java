package data.scripts.casino.shared;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

public final class GLColorUtils {
    private GLColorUtils() {}

    public static final float MAX_DELTA = 0.1f;

    public static float[] toGLComponents(Color color) {
        return new float[] {
            color.getRed() / 255f,
            color.getGreen() / 255f,
            color.getBlue() / 255f
        };
    }

    public static Color brighten(Color c) {
        int r = Math.min(255, (int)(c.getRed() + (255 - c.getRed()) * 0.35f));
        int g = Math.min(255, (int)(c.getGreen() + (255 - c.getGreen()) * 0.35f));
        int b = Math.min(255, (int)(c.getBlue() + (255 - c.getBlue()) * 0.35f));
        return new Color(r, g, b, c.getAlpha());
    }

    public static Color darken(Color c) {
        int r = (int)(c.getRed() * 0.65f);
        int g = (int)(c.getGreen() * 0.65f);
        int b = (int)(c.getBlue() * 0.65f);
        return new Color(r, g, b, c.getAlpha());
    }

    public static void setColorGL(Color color, float alphaMult) {
        GL11.glColor4f(
            color.getRed() / 255f,
            color.getGreen() / 255f,
            color.getBlue() / 255f,
            alphaMult
        );
    }

    public static void setColorGL(float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
    }

    public static void setColorGL(float[] components, float alphaMult) {
        GL11.glColor4f(components[0], components[1], components[2], alphaMult);
    }

    public static float capDelta(float amount) {
        return Math.min(amount, MAX_DELTA);
    }
}