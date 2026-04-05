package data.scripts.casino.shared;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;

import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.CardFlipAnimation;
import data.scripts.casino.cards.CardSprites;

public final class CardRenderingUtils {
    private CardRenderingUtils() {}

    private static final SettingsAPI settings = Global.getSettings();
    private static final SpriteAPI POKER_TABLE = settings.getSprite("poker", "table");

    public static final float PANEL_WIDTH = 1000f;
    public static final float PANEL_HEIGHT = 700f;
    public static final float CARD_WIDTH = 65f;
    public static final float CARD_HEIGHT = 94f;
    public static final float CARD_SPACING = 12f;
    public static final float MARGIN = 20f;
    public static final float BUTTON_WIDTH = 120f;
    public static final float BUTTON_HEIGHT = 35f;
    public static final float BUTTON_SPACING = 12f;

    public static final Color COLOR_PLAYER = new Color(100, 200, 255);
    public static final Color COLOR_OPPONENT = new Color(255, 100, 100);
    public static final Color COLOR_DEALER = new Color(255, 100, 100);
    public static final Color COLOR_BG_DARK = new Color(15, 15, 20);
    public static final Color COLOR_CARD_SHADOW = new Color(0, 0, 0, 150);

    public static void renderTableBackground(float x, float y, float w, float h, float alphaMult) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Misc.renderQuad(x, y, w, h, COLOR_BG_DARK, alphaMult);

        POKER_TABLE.setSize(w - 6, h * 0.7f);
        POKER_TABLE.render(x + 3, y + h * 0.15f);
    }

    public static void renderCardFaceUp(float x, float y, float cardWidth, float cardHeight,
            SpriteAPI cardSprite, float alphaMult) {
        Misc.renderQuad(x + 3, y - 3, cardWidth, cardHeight, COLOR_CARD_SHADOW, alphaMult * 0.3f);

        if (cardSprite != null) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            cardSprite.setSize(cardWidth, cardHeight);
            cardSprite.render(x, y);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }
    }

    

    public static void renderCardFaceDown(float x, float y, float cardWidth, float cardHeight, float alphaMult) {
        Misc.renderQuad(x + 3, y - 3, cardWidth, cardHeight, COLOR_CARD_SHADOW, alphaMult * 0.3f);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        SpriteAPI back = CardSprites.BACK_RED;
        back.setSize(cardWidth, cardHeight);
        back.render(x, y);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    public static void renderCardFaceDown(float x, float y, float alphaMult) {
        renderCardFaceDown(x, y, CARD_WIDTH, CARD_HEIGHT, alphaMult);
    }

    public static void renderCardAnimated(float x, float y, Card card, CardFlipAnimation anim, float alphaMult) {
        renderCardAnimated(x, y, CardSprites.get(card), anim, alphaMult);
    }

    public static void renderCardAnimated(float x, float y, float cardWidth, float cardHeight,
            SpriteAPI cardSprite, CardFlipAnimation anim, float alphaMult) {
        if (anim == null) {
            renderCardFaceUp(x, y, cardWidth, cardHeight, cardSprite, alphaMult);
            return;
        }

        if (anim.phase == CardFlipAnimation.Phase.HIDDEN) {
            renderCardFaceDown(x, y, cardWidth, cardHeight, alphaMult);
            return;
        }

        if (anim.phase == CardFlipAnimation.Phase.REVEALED) {
            renderCardFaceUp(x, y, cardWidth, cardHeight, cardSprite, alphaMult);
            return;
        }

        float widthScale = anim.getWidthScale();
        float cardCenterX = x + cardWidth / 2f;

        GL11.glPushMatrix();
        GL11.glTranslatef(cardCenterX, y, 0);
        GL11.glScalef(widthScale, 1f, 1f);
        GL11.glTranslatef(-cardCenterX, -y, 0);

        if (anim.shouldShowBack()) {
            renderCardFaceDown(x, y, cardWidth, cardHeight, alphaMult);
        } else {
            renderCardFaceUp(x, y, cardWidth, cardHeight, cardSprite, alphaMult);
        }

        GL11.glPopMatrix();
    }

    public static void renderCardAnimated(float x, float y, SpriteAPI cardSprite,
            CardFlipAnimation anim, float alphaMult) {
        renderCardAnimated(x, y, CARD_WIDTH, CARD_HEIGHT, cardSprite, anim, alphaMult);
    }

    public static void renderCardHighlightBorder(float x, float y, float cardWidth, float cardHeight,
            Color color, float alphaMult) {
        float borderThickness = 4f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alphaMult);

        GL11.glBegin(GL11.GL_QUADS);

        GL11.glVertex2f(x - borderThickness, y);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + cardHeight);
        GL11.glVertex2f(x - borderThickness, y + cardHeight);

        GL11.glVertex2f(x + cardWidth, y);
        GL11.glVertex2f(x + cardWidth + borderThickness, y);
        GL11.glVertex2f(x + cardWidth + borderThickness, y + cardHeight);
        GL11.glVertex2f(x + cardWidth, y + cardHeight);

        GL11.glVertex2f(x, y - borderThickness);
        GL11.glVertex2f(x + cardWidth, y - borderThickness);
        GL11.glVertex2f(x + cardWidth, y);
        GL11.glVertex2f(x, y);

        GL11.glVertex2f(x, y + cardHeight);
        GL11.glVertex2f(x + cardWidth, y + cardHeight);
        GL11.glVertex2f(x + cardWidth, y + cardHeight + borderThickness);
        GL11.glVertex2f(x, y + cardHeight + borderThickness);

        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    public static void renderCardHighlightBorder(float x, float y, Color color, float alphaMult) {
        renderCardHighlightBorder(x, y, CARD_WIDTH, CARD_HEIGHT, color, alphaMult);
    }
    }
