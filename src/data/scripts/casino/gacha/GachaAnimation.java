package data.scripts.casino.gacha;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.lwjgl.opengl.GL11;
import org.lwjgl.input.Keyboard;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Pair;

public class GachaAnimation extends BaseCustomUIPanelPlugin {

    /*
     * Y COORDINATE SYSTEM (OpenGL rendering in renderBelow):
     * - Y=0 at BOTTOM of screen, Y increases UPWARD
     * - Higher Y value = TOP of screen (visually)
     * - Lower Y value = BOTTOM of screen (visually)
     * - windowCenterY + offset = position ABOVE center (higher Y, top visually)
     * - windowCenterY - offset = position BELOW center (lower Y, bottom visually)
     * 
     * REEL SCROLLING:
     * - Ships scroll DOWNWARD visually (from top of window toward bottom)
     * - Increasing scrollOffset → ships move to lower Y → DOWNWARD visually
     * - scrollOffset resets to 0 after reaching SHIP_SLOT_HEIGHT (70f)
     * - New filler ships appear at top (higher Y) when scrollOffset resets
     * 
     * RESULT SHIP POSITIONING:
     * - resultShipVisualOffset: positive = ABOVE center (at TOP, waiting to enter)
     * - During stopping: offset decreases → ship moves DOWNWARD toward center
     * - Offset 0 = exactly at center
     * - Negative offset = BELOW center (overshoot position for bounce)
     */

    protected DialogCallbacks callbacks;
    protected CustomPanelAPI panel;
    protected PositionAPI p;

    protected List<GachaItem> allItems = new ArrayList<>();
    protected List<SlotReel> reels = new ArrayList<>();
    protected List<String> poolHullIds = new ArrayList<>();
    protected int nextReelToStop = 0;
    protected boolean allRevealed = false;
    protected float animationTimer = 0f;

    protected List<ParticleBurst> activeBursts = new ArrayList<>();
    protected List<RevealBurst> activeRevealBursts = new ArrayList<>();
    protected Map<String, SpriteAPI> spriteCache = new HashMap<>();
    protected Random random = new Random();

    protected static final float WINDOW_HEIGHT = 420f;
    protected static final float SHIP_SLOT_HEIGHT = 70f;
    protected static final float FRAME_BORDER = 5f;
    protected static final float RESULT_WINDOW_PADDING = 4f;

    protected static final float SINGLE_REEL_WIDTH = 220f;
    protected static final float MULTI_REEL_WIDTH = 85f;
    protected static final float MULTI_REEL_SPACING = 5f;

    protected static final float SCROLL_SPEED_BASE = 310f;
    protected static final float SCROLL_SPEED_VARIANCE = 30f;
    protected static final float STOP_DECEL_DURATION = 0.5f;
    protected static final int VISIBLE_SHIP_COUNT = 7;

    protected static final Color FRAME_COLOR = new Color(180, 150, 80);
    protected static final Color WINDOW_BG = new Color(35, 35, 50);
    protected static final Color CENTER_LINE_COLOR = new Color(255, 215, 0);
    protected static final Color BG_COLOR = new Color(25, 25, 35);
    protected static final Color FRAME_BG = new Color(40, 35, 50);

    public static Color getRarityColor(int rarity) {
        return switch(rarity) {
            case 5 -> new Color(255, 215, 0);
            case 4 -> new Color(200, 100, 255);
            case 3 -> new Color(100, 150, 255);
            case 2 -> new Color(100, 200, 100);
            case 1 -> new Color(150, 150, 150);
            default -> Color.WHITE;
        };
    }

    public static float[] toGLComponents(Color color) {
        return new float[] { color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f };
    }

    private static void renderQuad(float x, float y, float w, float h, float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    public interface GachaAnimationCallback {
        void onAnimationComplete(List<GachaItem> results);
    }

    public static class GachaItem {
        public String id;
        public String name;
        public String hullId;
        public int rarity;
        public Color color;
        public boolean revealed = false;

        public GachaItem(String id, String name, int rarity) {
            this.id = id;
            this.name = name;
            this.rarity = rarity;
            setColorForRarity();
        }

        private void setColorForRarity() {
            color = GachaAnimation.getRarityColor(rarity);
        }

        public void setHullId(String hullId) {
            this.hullId = hullId;
        }
    }

public class SlotReel {
        public int index;
        public String resultHullId;
        public int rarity;
        public Color rarityColor;
        public float reelCenterX;
        public float reelWidth;
        public float scrollOffset = 0f;
        public float scrollSpeed;
        public List<String> visibleHullIds = new ArrayList<>();
        public int resultShipIndex;
        public boolean isSpinning = true;
        public boolean isStopping = false;
        public boolean isStopped = false;
        public float stopTimer = 0f;
        public float stopDecelDuration = STOP_DECEL_DURATION;
        public float revealTimer = 0f;
        public boolean showStars = false;
        public float starAnimTimer = 0f;
        public float resultShipVisualOffset = 0f;
        public float stopDistance = 0f;
        public float bounceOffset = 0f;
        public boolean bouncePhase = false;
        public float bounceTimer = 0f;
        public static final float OVERSHOOT_AMOUNT = -30f;
        public static final float BOUNCE_DURATION = 0.3f;

        public SlotReel(int index, String resultHullId, int rarity, float reelCenterX, float reelWidth) {
            this.index = index;
            this.resultHullId = resultHullId;
            this.rarity = rarity;
            this.rarityColor = getRarityColor(rarity);
            this.reelCenterX = reelCenterX;
            this.reelWidth = reelWidth;
            this.scrollSpeed = SCROLL_SPEED_BASE + random.nextFloat() * SCROLL_SPEED_VARIANCE - SCROLL_SPEED_VARIANCE / 2f;
            this.resultShipIndex = VISIBLE_SHIP_COUNT / 2;
            this.scrollOffset = 0f;
            populateVisibleShips();
        }

        private Color getRarityColor(int r) {
            return GachaAnimation.getRarityColor(r);
        }

        private void populateVisibleShips() {
            visibleHullIds.clear();
            for (int i = 0; i < VISIBLE_SHIP_COUNT + 6; i++) {
                visibleHullIds.add(getRandomPoolHullId());
            }
        }

        public void advance(float amount) {
            if (isStopped) {
                if (!showStars && revealTimer > 0.15f) {
                    showStars = true;
                    starAnimTimer = 0f;
                }
                if (showStars) {
                    starAnimTimer += amount;
                }
                revealTimer += amount;
                return;
            }

            if (bouncePhase) {
                bounceTimer += amount;
                float progress = Math.min(1f, bounceTimer / BOUNCE_DURATION);
                float easeOut = 1f - (1f - progress) * (1f - progress);
                bounceOffset = -OVERSHOOT_AMOUNT * easeOut;
                
                if (progress >= 1f) {
                    bounceOffset = -OVERSHOOT_AMOUNT;
                    bouncePhase = false;
                    isStopping = false;
                    isStopped = true;
                    resultShipVisualOffset = 0f;
                    scrollOffset = 0f;
                    triggerRevealBurst(this);
                }
                return;
            }

            if (isStopping) {
                stopTimer += amount;
                float progress = Math.min(1f, stopTimer / stopDecelDuration);
                float remaining = 1f - progress;
                float speedFactor = remaining * remaining;
                
                float currentSpeed = scrollSpeed * speedFactor;
                scrollOffset += currentSpeed * amount;
                
                while (scrollOffset >= SHIP_SLOT_HEIGHT) {
                    scrollOffset -= SHIP_SLOT_HEIGHT;
                    shiftShips();
                }
                
                resultShipVisualOffset -= currentSpeed * amount;
                
                if (resultShipVisualOffset <= OVERSHOOT_AMOUNT) {
                    scrollOffset = 0f;
                    bouncePhase = true;
                    bounceTimer = 0f;
                    bounceOffset = 0f;
                    resultShipVisualOffset = OVERSHOOT_AMOUNT;
                }
                return;
            }

            if (isSpinning) {
                scrollOffset += scrollSpeed * amount;
                while (scrollOffset >= SHIP_SLOT_HEIGHT) {
                    scrollOffset -= SHIP_SLOT_HEIGHT;
                    shiftShips();
                }
            }
        }

        private void shiftShips() {
            visibleHullIds.remove(0);
            visibleHullIds.add(getRandomPoolHullId());
        }

        public void stop() {
            if (!isSpinning) return;
            isSpinning = false;
            isStopping = true;
            stopTimer = 0f;
            bouncePhase = false;
            bounceOffset = 0f;
            
            scrollOffset = 0f;
            
            stopDistance = 4 * SHIP_SLOT_HEIGHT;
            resultShipVisualOffset = stopDistance;
            
            stopDecelDuration = 3f * stopDistance / scrollSpeed;
        }

        public void instantStop() {
            isSpinning = false;
            isStopping = false;
            bouncePhase = false;
            isStopped = true;
            scrollOffset = 0f;
            resultShipVisualOffset = 0f;
            bounceOffset = 0f;
            revealTimer = 0.2f;
            showStars = true;
            starAnimTimer = 0.15f;
        }
    }

    public static class ParticleBurst {
        public float x, y;
        public Color color;
        public float lifetime;
        public float maxLifetime;
        public List<Pair<Float, Float>> particles;

        public ParticleBurst(float x, float y, Color color, int numParticles) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.maxLifetime = 0.5f;
            this.lifetime = 0f;
            this.particles = new ArrayList<>();

            for (int i = 0; i < numParticles; i++) {
                float angle = (float)(Math.random() * Math.PI * 2);
                float speed = 50f + (float)(Math.random() * 100f);
                particles.add(new Pair<>(angle, speed));
            }
        }

        public boolean advance(float amount) {
            lifetime += amount;
            return lifetime < maxLifetime;
        }

        public void render(float alphaMult) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            float progress = lifetime / maxLifetime;
            float alpha = (1f - progress) * alphaMult;
            float[] c = toGLComponents(color);

            for (Pair<Float, Float> particle : particles) {
                float angle = particle.one;
                float speed = particle.two;
                float dist = speed * lifetime;
                float px = x + (float)Math.cos(angle) * dist;
                float py = y + (float)Math.sin(angle) * dist;
                float size = 4f * (1f - progress * 0.5f);
                renderQuad(px - size/2, py - size/2, size, size, c[0], c[1], c[2], alpha);
            }
            
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }

    public static class RevealBurst {
        public float x, y;
        public Color color;
        public float lifetime;
        public float maxLifetime;
        public List<Pair<Float, Float>> particles;
        public float flashLifetime;
        public float flashMaxLifetime;
        public float flashSize;

        public RevealBurst(float x, float y, Color color) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.maxLifetime = 0.35f;
            this.lifetime = 0f;
            this.particles = new ArrayList<>();
            this.flashMaxLifetime = 0.12f;
            this.flashLifetime = 0f;
            this.flashSize = 80f;

            for (int i = 0; i < 30; i++) {
                float angle = (float)(Math.random() * Math.PI * 2);
                float speed = 130f + (float)(Math.random() * 80f);
                particles.add(new Pair<>(angle, speed));
            }
        }

        public boolean advance(float amount) {
            lifetime += amount;
            flashLifetime += amount;
            return lifetime < maxLifetime;
        }

        public void render(float alphaMult) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            
            float[] c = toGLComponents(color);

            float flashProgress = flashLifetime / flashMaxLifetime;
            if (flashProgress < 1f) {
                float flashAlpha = (1f - flashProgress) * alphaMult * 0.7f;
                float currentFlashSize = flashSize * (1f + flashProgress * 1.5f);
                renderQuad(x - currentFlashSize/2, y - currentFlashSize/2, currentFlashSize, currentFlashSize, c[0], c[1], c[2], flashAlpha);
            }

            float progress = lifetime / maxLifetime;
            float alpha = (1f - progress) * alphaMult;

            for (Pair<Float, Float> particle : particles) {
                float angle = particle.one;
                float speed = particle.two;
                float dist = speed * lifetime;
                float px = x + (float)Math.cos(angle) * dist;
                float py = y + (float)Math.sin(angle) * dist;
                float size = 6f * (1f - progress * 0.6f);
                renderQuad(px - size/2, py - size/2, size, size, c[0], c[1], c[2], alpha);
            }
            
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }

    protected GachaAnimationCallback callback;

    public GachaAnimation(List<GachaItem> itemsToAnimate, GachaAnimationCallback callback) {
        this.allItems.addAll(itemsToAnimate);
        this.callback = callback;
    }

    public void setPoolHullIds(List<String> hullIds) {
        if (hullIds != null && !hullIds.isEmpty()) {
            this.poolHullIds.addAll(hullIds);
        }
    }

    private String getRandomPoolHullId() {
        if (poolHullIds.isEmpty()) {
            List<ShipHullSpecAPI> allSpecs = Global.getSettings().getAllShipHullSpecs();
            for (ShipHullSpecAPI spec : allSpecs) {
                if (spec.getFleetPoints() >= 4 && spec.getFleetPoints() <= 40) {
                    poolHullIds.add(spec.getHullId());
                }
            }
        }
        if (poolHullIds.isEmpty()) return "kite";
        return poolHullIds.get(random.nextInt(poolHullIds.size()));
    }

    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.panel = panel;
        this.callbacks = callbacks;
        createReels();
    }

    private void createReels() {
        int numReels = allItems.size();
        boolean isSingle = numReels == 1;

        float reelWidth = isSingle ? SINGLE_REEL_WIDTH : MULTI_REEL_WIDTH;
        float spacing = isSingle ? 0f : MULTI_REEL_SPACING;
        float totalWidth = numReels * reelWidth + (numReels - 1) * spacing;
        float startX = -totalWidth / 2f;

        for (int i = 0; i < numReels; i++) {
            GachaItem item = allItems.get(i);
            float reelCenterX = startX + i * (reelWidth + spacing) + reelWidth / 2f;

            SlotReel reel = new SlotReel(i, item.hullId, item.rarity, reelCenterX, reelWidth);
            reels.add(reel);
        }
    }

    public void positionChanged(PositionAPI position) {
        this.p = position;
    }

    public void renderBelow(float alphaMult) {
        if (p == null) return;

        float panelX = p.getX();
        float panelY = p.getY();
        float panelW = p.getWidth();
        float panelH = p.getHeight();
        float panelCenterX = p.getCenterX();
        float panelCenterY = p.getCenterY();

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        renderColorQuad(panelX, panelY, panelW, panelH, BG_COLOR, alphaMult);

        float windowBottom = panelCenterY - WINDOW_HEIGHT / 2f;

        float frameY = windowBottom - FRAME_BORDER;
        float frameH = WINDOW_HEIGHT + FRAME_BORDER * 2;
        renderColorQuad(panelX, frameY, panelW, frameH, FRAME_BG, alphaMult);

        float screenScale = Global.getSettings().getScreenScaleMult();

        for (SlotReel reel : reels) {
            float reelLeft = panelCenterX + reel.reelCenterX - reel.reelWidth / 2f + FRAME_BORDER;
            float reelRight = reelLeft + reel.reelWidth - FRAME_BORDER * 2;
            float reelContentWidth = reelRight - reelLeft;

            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(
                (int)(reelLeft * screenScale),
                (int)(windowBottom * screenScale),
                (int)(reelContentWidth * screenScale),
                (int)(WINDOW_HEIGHT * screenScale)
            );

            renderColorQuad(reelLeft, windowBottom, reelContentWidth, WINDOW_HEIGHT, WINDOW_BG, alphaMult);

            renderReelContent(reel, reelLeft, reelRight, panelCenterY, alphaMult);

            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            renderReelFrame(reel, panelCenterX, windowBottom, alphaMult);
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        for (ParticleBurst burst : activeBursts) {
            burst.render(alphaMult);
        }
        for (RevealBurst burst : activeRevealBursts) {
            burst.render(alphaMult);
        }
    }

    private void renderColorQuad(float x, float y, float w, float h, Color color, float alphaMult) {
        float[] c = toGLComponents(color);
        renderQuad(x, y, w, h, c[0], c[1], c[2], alphaMult);
    }

    private void renderReelContent(SlotReel reel, float reelLeft, float reelRight, float windowCenterY, float alphaMult) {
        float reelWidth = reelRight - reelLeft;
        float reelCenterX = reelLeft + reelWidth / 2f;
        float maxShipWidth = reelWidth * 0.95f;
        float maxShipHeight = SHIP_SLOT_HEIGHT * 0.85f;
        float windowHalfHeight = WINDOW_HEIGHT / 2f;

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        for (int i = 0; i < reel.visibleHullIds.size(); i++) {
            if (reel.isStopping || reel.bouncePhase || reel.isStopped) {
                if (i == reel.resultShipIndex) continue;
            }
            
            float shipCenterY = windowCenterY + (i - reel.resultShipIndex) * SHIP_SLOT_HEIGHT - reel.scrollOffset + reel.bounceOffset;
            float distanceFromCenter = Math.abs(shipCenterY - windowCenterY);
            
            if (distanceFromCenter > windowHalfHeight + SHIP_SLOT_HEIGHT) continue;

            float fadeAlpha = Math.max(0.7f, 1f - Math.min(1f, distanceFromCenter / windowHalfHeight)) * alphaMult;

            String hullId = reel.visibleHullIds.get(i);
            SpriteAPI sprite = getShipSprite(hullId);
            renderShipSprite(sprite, reelCenterX, shipCenterY, maxShipWidth, maxShipHeight, fadeAlpha);
        }
        
        if (reel.isStopping || reel.bouncePhase || reel.isStopped) {
            float resultY = windowCenterY + reel.resultShipVisualOffset + reel.bounceOffset;
            SpriteAPI resultSprite = getShipSprite(reel.resultHullId);
            float resultAlpha = reel.isStopped ? alphaMult : Math.max(0.7f, 1f - Math.abs(reel.resultShipVisualOffset + reel.bounceOffset) / windowHalfHeight) * alphaMult;
            renderShipSprite(resultSprite, reelCenterX, resultY, maxShipWidth, maxShipHeight, resultAlpha);
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (reel.isStopped) {
            float glowIntensity = Math.min(1f, reel.revealTimer * 2.5f);
            
            if (glowIntensity > 0f && reel.rarity >= 3) {
                float[] c = toGLComponents(reel.rarityColor);

                for (int gl = 0; gl < 3; gl++) {
                    float glowSize = 65f + gl * 25f;
                    float glowA = glowIntensity * alphaMult * 0.18f * (1f - gl * 0.25f);
                    renderQuad(reelCenterX - glowSize/2, windowCenterY - glowSize/2, glowSize, glowSize, c[0], c[1], c[2], glowA);
                }
            }

            if (reel.showStars) {
                float starsY = windowCenterY + SHIP_SLOT_HEIGHT * 0.3f;
                renderStars(reelCenterX, starsY, reel.rarity, reel.starAnimTimer, alphaMult);
            }
        }
        
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private void renderShipSprite(SpriteAPI sprite, float centerX, float centerY, float maxWidth, float maxHeight, float alphaMult) {
        if (sprite == null) {
            float[] c = toGLComponents(new Color(80, 80, 100));
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            renderQuad(centerX - maxWidth/2, centerY - maxHeight/2, maxWidth, maxHeight, c[0], c[1], c[2], alphaMult * 0.5f);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            return;
        }

        float spriteW = sprite.getWidth();
        float spriteH = sprite.getHeight();
        float scale = Math.min(maxWidth / spriteW, maxHeight / spriteH);
        float scaledW = spriteW * scale;
        float scaledH = spriteH * scale;

        sprite.setSize(scaledW, scaledH);
        sprite.setColor(Color.WHITE);
        sprite.setAlphaMult(alphaMult);
        sprite.setNormalBlend();
        sprite.renderAtCenter(centerX, centerY);
    }

    private void renderStars(float centerX, float centerY, int rarity, float animTimer, float alphaMult) {
        float[] c = toGLComponents(rarity >= 4 ? new Color(255, 215, 0) : new Color(255, 255, 120));
        
        float baseSize = 6f;
        float spacing = 10f;
        float totalWidth = (rarity - 1) * spacing;
        float startX = centerX - totalWidth / 2f;

        for (int i = 0; i < rarity; i++) {
            float appearTime = i * 0.04f;
            if (animTimer < appearTime) continue;

            float starProgress = Math.min(1f, (animTimer - appearTime) / 0.1f);
            float starScale = 1.3f - 0.3f * starProgress;

            float starX = startX + i * spacing;
            float size = baseSize * starScale;
            float half = size / 2f;
            float quarter = size / 4f;

            GL11.glColor4f(c[0], c[1], c[2], alphaMult);
            
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(starX - quarter, centerY - half);
            GL11.glVertex2f(starX + quarter, centerY - half);
            GL11.glVertex2f(starX + quarter, centerY + half);
            GL11.glVertex2f(starX - quarter, centerY + half);
            GL11.glEnd();
            
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(starX - half, centerY - quarter);
            GL11.glVertex2f(starX + half, centerY - quarter);
            GL11.glVertex2f(starX + half, centerY + quarter);
            GL11.glVertex2f(starX - half, centerY + quarter);
            GL11.glEnd();
        }
    }

    private void renderReelFrame(SlotReel reel, float panelCenterX, float windowBottom, float alphaMult) {
        float reelLeft = panelCenterX + reel.reelCenterX - reel.reelWidth / 2f;
        float reelRight = reelLeft + reel.reelWidth;
        float windowTop = windowBottom + WINDOW_HEIGHT;
        float border = FRAME_BORDER;

        float[] fc = toGLComponents(FRAME_COLOR);
        GL11.glColor4f(fc[0], fc[1], fc[2], alphaMult);

        renderQuad(reelLeft, windowBottom - border, reel.reelWidth, border, fc[0], fc[1], fc[2], alphaMult);
        renderQuad(reelLeft, windowTop, reel.reelWidth, border, fc[0], fc[1], fc[2], alphaMult);
        renderQuad(reelLeft, windowBottom - border, border, WINDOW_HEIGHT + border * 2, fc[0], fc[1], fc[2], alphaMult);
        renderQuad(reelRight - border, windowBottom - border, border, WINDOW_HEIGHT + border * 2, fc[0], fc[1], fc[2], alphaMult);

        float windowCenterY = windowBottom + WINDOW_HEIGHT / 2f;
        float resultWindowH = SHIP_SLOT_HEIGHT + RESULT_WINDOW_PADDING * 2;
        float resultWindowY = windowCenterY - resultWindowH / 2f;
        float resultWindowW = reel.reelWidth - border * 2;
        float resultWindowX = reelLeft + border;

        float highlightThickness = 3f;
        Color highlightColor = reel.isStopped ? reel.rarityColor : CENTER_LINE_COLOR;
        float highlightAlpha = reel.isStopped ? 1f : 0.7f;

        float[] hc = toGLComponents(highlightColor);
        float hAlpha = alphaMult * highlightAlpha;

        renderQuad(resultWindowX, resultWindowY, resultWindowW, highlightThickness, hc[0], hc[1], hc[2], hAlpha);
        renderQuad(resultWindowX, resultWindowY + resultWindowH - highlightThickness, resultWindowW, highlightThickness, hc[0], hc[1], hc[2], hAlpha);

        if (reel.isStopped) {
            renderQuad(resultWindowX, resultWindowY, highlightThickness, resultWindowH, hc[0], hc[1], hc[2], hAlpha * 0.6f);
            renderQuad(resultWindowX + resultWindowW - highlightThickness, resultWindowY, highlightThickness, resultWindowH, hc[0], hc[1], hc[2], hAlpha * 0.6f);
        }
        
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private SpriteAPI getShipSprite(String hullId) {
        if (hullId == null || hullId.isEmpty()) return null;
        if (spriteCache.containsKey(hullId)) return spriteCache.get(hullId);

        try {
            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
            if (spec == null) {
                spriteCache.put(hullId, null);
                return null;
            }

            SpriteAPI sprite = Global.getSettings().getSprite(spec.getSpriteName());
            spriteCache.put(hullId, sprite);
            return sprite;
        } catch (Exception e) {
            spriteCache.put(hullId, null);
            return null;
        }
    }

    public void advance(float amount) {
        if (p == null) return;

        animationTimer += amount;

        for (SlotReel reel : reels) {
            reel.advance(amount);
        }

        activeBursts.removeIf(burst -> !burst.advance(amount));
        activeRevealBursts.removeIf(burst -> !burst.advance(amount));

        if (!allRevealed) {
            if (allReelsStopped()) {
                float minRevealTime = 0.35f;
                boolean readyToComplete = true;
                for (SlotReel reel : reels) {
                    if (reel.revealTimer < minRevealTime) {
                        readyToComplete = false;
                        break;
                    }
                }

                if (readyToComplete) {
                    markAllRevealed();
                }
            }
        }
    }

    public void processInput(List<InputEventAPI> events) {
        if (p == null) return;

        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            if (event.isLMBDownEvent()) {
                event.consume();
                handleClick();
                return;
            }

            if (event.isKeyDownEvent()) {
                int key = event.getEventValue();

                if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_SPACE || key == Keyboard.KEY_RETURN) {
                    event.consume();
                    handleClick();
                    return;
                }
            }
        }
    }

    private void handleClick() {
        if (allRevealed) {
            closeAnimation();
            return;
        }

        for (int i = nextReelToStop; i < reels.size(); i++) {
            SlotReel reel = reels.get(i);
            if (reel.isSpinning) {
                reel.stop();
                nextReelToStop = i + 1;
                return;
            }
        }

        for (SlotReel reel : reels) {
            if (reel.isStopping) {
                reel.instantStop();
            }
        }

        if (allReelsStopped()) {
            markAllRevealed();
        }
    }

    private void triggerRevealBurst(SlotReel reel) {
        float panelCenterX = p.getCenterX();
        float panelCenterY = p.getCenterY();
        float centerX = panelCenterX + reel.reelCenterX;

        activeRevealBursts.add(new RevealBurst(centerX, panelCenterY, reel.rarityColor));
        activeBursts.add(new ParticleBurst(centerX, panelCenterY, reel.rarityColor, 10 + reel.rarity * 3));
    }

    private boolean allReelsStopped() {
        for (SlotReel reel : reels) {
            if (!reel.isStopped) return false;
        }
        return true;
    }

    private void markAllRevealed() {
        allRevealed = true;
        for (GachaItem item : allItems) {
            item.revealed = true;
        }
    }

    private void closeAnimation() {
        if (callback != null) {
            callback.onAnimationComplete(allItems);
        }
        if (callbacks != null) {
            callbacks.dismissDialog();
        }
    }

    public void startAnimation() {
        animationTimer = 0f;
        nextReelToStop = 0;
        allRevealed = false;
        activeBursts.clear();
        activeRevealBursts.clear();
        spriteCache.clear();

        for (SlotReel reel : reels) {
            reel.isSpinning = true;
            reel.isStopping = false;
            reel.isStopped = false;
            reel.bouncePhase = false;
            reel.scrollOffset = 0f;
            reel.resultShipVisualOffset = 0f;
            reel.bounceOffset = 0f;
            reel.revealTimer = 0f;
            reel.showStars = false;
            reel.starAnimTimer = 0f;
            reel.populateVisibleShips();
        }
    }
}