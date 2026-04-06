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

    public static final float STACK_BAR_WIDTH = 10f;
    public static final float STACK_BAR_MAX_HEIGHT = 90f;
    public static final float STACK_BAR_GAP = 2f;
    public static final float STACK_BAR_CARD_GAP = 4f;

    public static final Color STACK_BAR_BG = new Color(40, 40, 50);
    public static final Color STACK_BAR_BET = new Color(255, 255, 255);
    public static final Color STACK_BAR_GREEN = new Color(100, 200, 100);
    public static final Color STACK_BAR_YELLOW = new Color(255, 200, 50);
    public static final Color STACK_BAR_RED = new Color(255, 80, 80);

    public static final float CHIP_HEIGHT = 4f;
    public static final float CHIP_GAP = 1f;
    public static final float CHIP_SEGMENT_HEIGHT = CHIP_HEIGHT + CHIP_GAP;
    public static final float SMILE_CURVE_DEPTH = 1.5f;

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

    public static void renderStackBars(float cardStartX, float cardY, int stack, int bet, int maxStack, float alphaMult) {
        if (maxStack <= 0) return;

        int totalEffective = stack + bet;
        int numBars = Math.max(1, (int) Math.ceil(totalEffective / (float) maxStack));
        
        int[] fillAmounts = new int[numBars];
        for (int i = 0; i < numBars; i++) {
            fillAmounts[i] = Math.min(Math.max(0, totalEffective - i * maxStack), maxStack);
        }
        
        int[] whiteAmounts = new int[numBars];
        int[] coloredAmounts = new int[numBars];
        System.arraycopy(fillAmounts, 0, coloredAmounts, 0, numBars);
        
        int remainingBet = bet;
        for (int i = 0; i < numBars && remainingBet > 0; i++) {
            int deduct = Math.min(remainingBet, coloredAmounts[i]);
            whiteAmounts[i] = deduct;
            coloredAmounts[i] -= deduct;
            remainingBet -= deduct;
        }
        
        float primaryBarX = cardStartX - STACK_BAR_CARD_GAP - STACK_BAR_WIDTH;
        
        Color primaryColor = getStackColor(stack, stack, maxStack);
        renderSingleBarBg(primaryBarX, cardY, alphaMult);
        float primaryColoredHeight = (coloredAmounts[0] / (float) maxStack) * STACK_BAR_MAX_HEIGHT;
        if (primaryColoredHeight > 0) {
            renderChipStackWithSmile(primaryBarX, cardY, primaryColoredHeight, primaryColor, alphaMult);
        }
        float primaryWhiteHeight = (whiteAmounts[0] / (float) maxStack) * STACK_BAR_MAX_HEIGHT;
        if (primaryWhiteHeight > 0) {
            renderChipStackWithSmile(primaryBarX, cardY + primaryColoredHeight, primaryWhiteHeight, STACK_BAR_BET, alphaMult);
        }
        
        for (int i = 1; i < numBars; i++) {
            float overflowBarX = primaryBarX - i * (STACK_BAR_WIDTH + STACK_BAR_GAP);
            
            renderSingleBarBg(overflowBarX, cardY, alphaMult);
            float overflowColoredHeight = (coloredAmounts[i] / (float) maxStack) * STACK_BAR_MAX_HEIGHT;
            if (overflowColoredHeight > 0) {
                renderChipStackWithSmile(overflowBarX, cardY, overflowColoredHeight, STACK_BAR_GREEN, alphaMult);
            }
            float overflowWhiteHeight = (whiteAmounts[i] / (float) maxStack) * STACK_BAR_MAX_HEIGHT;
            if (overflowWhiteHeight > 0) {
                renderChipStackWithSmile(overflowBarX, cardY + overflowColoredHeight, overflowWhiteHeight, STACK_BAR_BET, alphaMult);
            }
        }
    }

    private static void renderSingleBarBg(float x, float y, float alphaMult) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        setColorGL(STACK_BAR_BG, alphaMult);
        
        renderCurvedBarShape(x, y, STACK_BAR_MAX_HEIGHT);
        
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
    
    private static void renderCurvedBarShape(float x, float y, float height) {
        int segments = 10;
        float segmentWidth = STACK_BAR_WIDTH / segments;
        
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= segments; i++) {
            float segX = x + i * segmentWidth;
            float normalizedPos = i / (float) segments;
            float curveOffset = SMILE_CURVE_DEPTH * 4f * normalizedPos * (1f - normalizedPos);
            float topY = y - curveOffset;
            float bottomY = y + height + curveOffset;
            
            GL11.glVertex2f(segX, topY);
            GL11.glVertex2f(segX, bottomY);
        }
        GL11.glEnd();
    }

    private static Color getStackColor(int stack, int remaining, int maxStack) {
        if (stack > maxStack) return STACK_BAR_GREEN;
        float ratio = remaining / (float) maxStack;
        if (ratio > 0.5f) return STACK_BAR_GREEN;
        if (ratio > 0.25f) return STACK_BAR_YELLOW;
        return STACK_BAR_RED;
    }

    private static void renderChipStackWithSmile(float x, float y, float height, Color color, float alphaMult) {
        if (height <= 0) return;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        setColorGL(color, alphaMult);
        
        renderCurvedFillShape(x, y, height);

        int numGaps = (int) (height / CHIP_SEGMENT_HEIGHT);
        setColorGL(STACK_BAR_BG, alphaMult);
        for (int i = 0; i < numGaps; i++) {
            float gapY = y + CHIP_HEIGHT + i * CHIP_SEGMENT_HEIGHT;
            renderSmileCurvedGap(x, gapY, STACK_BAR_WIDTH, CHIP_GAP, SMILE_CURVE_DEPTH);
        }

        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
    
    private static void renderCurvedFillShape(float x, float y, float height) {
        int segments = 10;
        float segmentWidth = STACK_BAR_WIDTH / segments;
        
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= segments; i++) {
            float segX = x + i * segmentWidth;
            float normalizedPos = i / (float) segments;
            float curveOffset = SMILE_CURVE_DEPTH * 4f * normalizedPos * (1f - normalizedPos);
            float topY = y - curveOffset;
            float bottomY = y + height + curveOffset;
            
            GL11.glVertex2f(segX, topY);
            GL11.glVertex2f(segX, bottomY);
        }
        GL11.glEnd();
    }

    private static void renderSmileCurvedGap(float x, float y, float width, float gapHeight, float curveDepth) {
        int segments = 10;
        float segmentWidth = width / segments;
        
        for (int i = 0; i < segments; i++) {
            float segX = x + i * segmentWidth;
            float normalizedPos = (i + 0.5f) / segments;
            float curveOffset = curveDepth * 4f * normalizedPos * (1f - normalizedPos);
            float segY = y - curveOffset;
            float segHeight = gapHeight;
            
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(segX, segY);
            GL11.glVertex2f(segX + segmentWidth, segY);
            GL11.glVertex2f(segX + segmentWidth, segY + segHeight);
            GL11.glVertex2f(segX, segY + segHeight);
            GL11.glEnd();
        }
    }

    private static void setColorGL(Color color, float alphaMult) {
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alphaMult);
    }
}
