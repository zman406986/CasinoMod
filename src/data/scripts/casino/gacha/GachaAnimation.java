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
            color = switch(rarity) {
                case 1 -> new Color(150, 150, 150);
                case 2 -> new Color(100, 200, 100);
                case 3 -> new Color(100, 150, 255);
                case 4 -> new Color(200, 100, 255);
                case 5 -> new Color(255, 215, 0);
                default -> Color.WHITE;
            };
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
        public float revealTimer = 0f;
        public boolean showStars = false;
        public float starAnimTimer = 0f;

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
            return switch(r) {
                case 5 -> new Color(255, 215, 0);
                case 4 -> new Color(200, 100, 255);
                case 3 -> new Color(100, 150, 255);
                case 2 -> new Color(100, 200, 100);
                default -> new Color(150, 150, 150);
            };
        }

        private void populateVisibleShips() {
            visibleHullIds.clear();
            for (int i = 0; i < VISIBLE_SHIP_COUNT + 6; i++) {
                visibleHullIds.add(getRandomPoolHullId());
            }
            if (resultHullId != null) {
                visibleHullIds.set(resultShipIndex, resultHullId);
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

            if (isStopping) {
                stopTimer += amount;
                float progress = Math.min(1f, stopTimer / STOP_DECEL_DURATION);
                
                if (progress < 1f) {
                    float remaining = 1f - progress;
                    float speedFactor = remaining * remaining * remaining;
                    float currentSpeed = scrollSpeed * speedFactor;
                    scrollOffset += currentSpeed * amount;
                    
                    while (scrollOffset >= SHIP_SLOT_HEIGHT) {
                        scrollOffset -= SHIP_SLOT_HEIGHT;
                        shiftShips();
                    }
                }
                
                if (progress >= 1f) {
                    isStopping = false;
                    isStopped = true;
                    alignToResult();
                    triggerRevealBurst(this);
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

        private void alignToResult() {
            scrollOffset = 0f;
            if (resultHullId != null) {
                visibleHullIds.set(resultShipIndex, resultHullId);
            }
        }

        private void shiftShips() {
            if (!visibleHullIds.isEmpty()) {
                visibleHullIds.remove(0);
            }
            visibleHullIds.add(getRandomPoolHullId());
        }

        public void stop() {
            if (!isSpinning) return;
            isSpinning = false;
            isStopping = true;
            stopTimer = 0f;
            scrollOffset = 0f;
            populateVisibleShips();
        }

        public void instantStop() {
            isSpinning = false;
            isStopping = false;
            isStopped = true;
            scrollOffset = 0f;
            revealTimer = 0.2f;
            showStars = true;
            starAnimTimer = 0.15f;
            if (resultHullId != null) {
                visibleHullIds.set(resultShipIndex, resultHullId);
            }
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
            
            float r = color.getRed() / 255f;
            float g = color.getGreen() / 255f;
            float b = color.getBlue() / 255f;

            for (Pair<Float, Float> particle : particles) {
                float angle = particle.one;
                float speed = particle.two;
                float dist = speed * lifetime;
                float px = x + (float)Math.cos(angle) * dist;
                float py = y + (float)Math.sin(angle) * dist;

                float size = 4f * (1f - progress * 0.5f);
                
                GL11.glColor4f(r, g, b, alpha);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(px - size/2, py - size/2);
                GL11.glVertex2f(px + size/2, py - size/2);
                GL11.glVertex2f(px + size/2, py + size/2);
                GL11.glVertex2f(px - size/2, py + size/2);
                GL11.glEnd();
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
            
            float r = color.getRed() / 255f;
            float g = color.getGreen() / 255f;
            float b = color.getBlue() / 255f;

            float flashProgress = flashLifetime / flashMaxLifetime;
            if (flashProgress < 1f) {
                float flashAlpha = (1f - flashProgress) * alphaMult * 0.7f;
                float currentFlashSize = flashSize * (1f + flashProgress * 1.5f);
                float halfSize = currentFlashSize / 2f;
                
                GL11.glColor4f(r, g, b, flashAlpha);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(x - halfSize, y - halfSize);
                GL11.glVertex2f(x + halfSize, y - halfSize);
                GL11.glVertex2f(x + halfSize, y + halfSize);
                GL11.glVertex2f(x - halfSize, y + halfSize);
                GL11.glEnd();
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
                float halfSize = size / 2f;
                
                GL11.glColor4f(r, g, b, alpha);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(px - halfSize, py - halfSize);
                GL11.glVertex2f(px + halfSize, py - halfSize);
                GL11.glVertex2f(px + halfSize, py + halfSize);
                GL11.glVertex2f(px - halfSize, py + halfSize);
                GL11.glEnd();
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

        float windowCenterY = panelCenterY;
        float windowBottom = windowCenterY - WINDOW_HEIGHT / 2f;

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

            renderReelContent(reel, reelLeft, reelRight, windowCenterY, alphaMult);

            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            renderReelFrame(reel, panelCenterX, windowBottom, WINDOW_HEIGHT, alphaMult);
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
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = alphaMult;
        
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    private void renderReelContent(SlotReel reel, float reelLeft, float reelRight, float windowCenterY, float alphaMult) {
        float reelWidth = reelRight - reelLeft;
        float reelCenterX = reelLeft + reelWidth / 2f;
        float maxShipWidth = reelWidth * 0.95f;
        float maxShipHeight = SHIP_SLOT_HEIGHT * 0.85f;

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        for (int i = 0; i < reel.visibleHullIds.size(); i++) {
            float shipCenterY = windowCenterY + (i - reel.resultShipIndex) * SHIP_SLOT_HEIGHT - reel.scrollOffset;

            if (shipCenterY < windowCenterY - WINDOW_HEIGHT / 2f - SHIP_SLOT_HEIGHT ||
                shipCenterY > windowCenterY + WINDOW_HEIGHT / 2f + SHIP_SLOT_HEIGHT) {
                continue;
            }

            String hullId = reel.visibleHullIds.get(i);
            SpriteAPI sprite = getShipSprite(hullId);

            float distanceFromCenter = Math.abs(shipCenterY - windowCenterY);
            float fadeAlpha = 1f - Math.min(1f, distanceFromCenter / (WINDOW_HEIGHT / 2f));
            fadeAlpha = Math.max(0.7f, fadeAlpha);
            fadeAlpha *= alphaMult;

            boolean isResultShip = (i == reel.resultShipIndex);

            float dimAlpha = fadeAlpha;
            if (isResultShip && !reel.isStopped) {
                dimAlpha = fadeAlpha * 0.9f;
            }
            renderShipSprite(sprite, reelCenterX, shipCenterY, maxShipWidth, maxShipHeight, Color.WHITE, dimAlpha);
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (int i = 0; i < reel.visibleHullIds.size(); i++) {
            float shipCenterY = windowCenterY + (i - reel.resultShipIndex) * SHIP_SLOT_HEIGHT - reel.scrollOffset;

            if (shipCenterY < windowCenterY - WINDOW_HEIGHT / 2f - SHIP_SLOT_HEIGHT ||
                shipCenterY > windowCenterY + WINDOW_HEIGHT / 2f + SHIP_SLOT_HEIGHT) {
                continue;
            }

            boolean isResultShip = (i == reel.resultShipIndex);

            if (reel.isStopped && isResultShip) {
                float glowIntensity = Math.min(1f, reel.revealTimer * 2.5f);
                
                if (glowIntensity > 0f && reel.rarity >= 3) {
                    float r = reel.rarityColor.getRed() / 255f;
                    float g = reel.rarityColor.getGreen() / 255f;
                    float b = reel.rarityColor.getBlue() / 255f;
                    
                    float distanceFromCenter = Math.abs(shipCenterY - windowCenterY);
                    float fadeAlpha = 1f - Math.min(1f, distanceFromCenter / (WINDOW_HEIGHT / 2f));
                    fadeAlpha = Math.max(0.7f, fadeAlpha);
                    fadeAlpha *= alphaMult;

                    for (int gl = 0; gl < 3; gl++) {
                        float glowSize = 65f + gl * 25f;
                        float glowA = glowIntensity * fadeAlpha * 0.18f * (1f - gl * 0.25f);
                        float halfSize = glowSize / 2f;
                        
                        GL11.glColor4f(r, g, b, glowA);
                        GL11.glBegin(GL11.GL_QUADS);
                        GL11.glVertex2f(reelCenterX - halfSize, shipCenterY - halfSize);
                        GL11.glVertex2f(reelCenterX + halfSize, shipCenterY - halfSize);
                        GL11.glVertex2f(reelCenterX + halfSize, shipCenterY + halfSize);
                        GL11.glVertex2f(reelCenterX - halfSize, shipCenterY + halfSize);
                        GL11.glEnd();
                    }
                }

                if (reel.showStars) {
                    float starsY = shipCenterY + SHIP_SLOT_HEIGHT * 0.3f;
                    renderStars(reelCenterX, starsY, reel.rarity, reel.starAnimTimer, alphaMult);
                }
            }
        }
        
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private void renderShipSprite(SpriteAPI sprite, float centerX, float centerY, float maxWidth, float maxHeight, Color color, float alphaMult) {
        if (sprite == null) {
            Color placeholderColor = new Color(80, 80, 100);
            float r = placeholderColor.getRed() / 255f;
            float g = placeholderColor.getGreen() / 255f;
            float b = placeholderColor.getBlue() / 255f;
            float a = alphaMult * 0.5f;
            float halfW = maxWidth / 2f;
            float halfH = maxHeight / 2f;
            
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(r, g, b, a);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(centerX - halfW, centerY - halfH);
            GL11.glVertex2f(centerX + halfW, centerY - halfH);
            GL11.glVertex2f(centerX + halfW, centerY + halfH);
            GL11.glVertex2f(centerX - halfW, centerY + halfH);
            GL11.glEnd();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            return;
        }

        float spriteW = sprite.getWidth();
        float spriteH = sprite.getHeight();
        float scale = Math.min(maxWidth / spriteW, maxHeight / spriteH);
        float scaledW = spriteW * scale;
        float scaledH = spriteH * scale;

        sprite.setSize(scaledW, scaledH);
        sprite.setColor(color);
        sprite.setAlphaMult(alphaMult);
        sprite.setNormalBlend();
        sprite.renderAtCenter(centerX, centerY);
        sprite.setColor(Color.WHITE);
    }

    private void renderStars(float centerX, float centerY, int rarity, float animTimer, float alphaMult) {
        Color starColor = rarity >= 4 ? new Color(255, 215, 0) : new Color(255, 255, 120);
        float r = starColor.getRed() / 255f;
        float g = starColor.getGreen() / 255f;
        float b = starColor.getBlue() / 255f;
        
        float baseSize = 6f;
        float spacing = 10f;
        float totalWidth = (rarity - 1) * spacing;
        float startX = centerX - totalWidth / 2f;

        for (int i = 0; i < rarity; i++) {
            float appearTime = i * 0.04f;
            float starScale = 1f;

            if (animTimer >= appearTime) {
                float starProgress = Math.min(1f, (animTimer - appearTime) / 0.1f);
                starScale = 1.3f - 0.3f * starProgress;
            } else {
                continue;
            }

            float starX = startX + i * spacing;
            float size = baseSize * starScale;
            float half = size / 2f;
            float quarter = size / 4f;

            GL11.glColor4f(r, g, b, alphaMult);
            
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

    private void renderReelFrame(SlotReel reel, float panelCenterX, float windowBottom, float windowHeight, float alphaMult) {
        float reelLeft = panelCenterX + reel.reelCenterX - reel.reelWidth / 2f;
        float reelRight = reelLeft + reel.reelWidth;
        float windowTop = windowBottom + windowHeight;
        float border = FRAME_BORDER;

        float fr = FRAME_COLOR.getRed() / 255f;
        float fg = FRAME_COLOR.getGreen() / 255f;
        float fb = FRAME_COLOR.getBlue() / 255f;

        GL11.glColor4f(fr, fg, fb, alphaMult);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(reelLeft, windowBottom - border);
        GL11.glVertex2f(reelLeft + reel.reelWidth, windowBottom - border);
        GL11.glVertex2f(reelLeft + reel.reelWidth, windowBottom);
        GL11.glVertex2f(reelLeft, windowBottom);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(reelLeft, windowTop);
        GL11.glVertex2f(reelLeft + reel.reelWidth, windowTop);
        GL11.glVertex2f(reelLeft + reel.reelWidth, windowTop + border);
        GL11.glVertex2f(reelLeft, windowTop + border);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(reelLeft, windowBottom - border);
        GL11.glVertex2f(reelLeft + border, windowBottom - border);
        GL11.glVertex2f(reelLeft + border, windowTop + border);
        GL11.glVertex2f(reelLeft, windowTop + border);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(reelRight - border, windowBottom - border);
        GL11.glVertex2f(reelRight, windowBottom - border);
        GL11.glVertex2f(reelRight, windowTop + border);
        GL11.glVertex2f(reelRight - border, windowTop + border);
        GL11.glEnd();

        float windowCenterY = windowBottom + windowHeight / 2f;
        float resultWindowH = SHIP_SLOT_HEIGHT + RESULT_WINDOW_PADDING * 2;
        float resultWindowY = windowCenterY - resultWindowH / 2f;
        float resultWindowW = reel.reelWidth - border * 2;
        float resultWindowX = reelLeft + border;

        float highlightThickness = 3f;
        Color highlightColor = reel.isStopped ? reel.rarityColor : CENTER_LINE_COLOR;
        float highlightAlpha = reel.isStopped ? 1f : 0.7f;

        float hr = highlightColor.getRed() / 255f;
        float hg = highlightColor.getGreen() / 255f;
        float hb = highlightColor.getBlue() / 255f;

        GL11.glColor4f(hr, hg, hb, alphaMult * highlightAlpha);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(resultWindowX, resultWindowY);
        GL11.glVertex2f(resultWindowX + resultWindowW, resultWindowY);
        GL11.glVertex2f(resultWindowX + resultWindowW, resultWindowY + highlightThickness);
        GL11.glVertex2f(resultWindowX, resultWindowY + highlightThickness);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(resultWindowX, resultWindowY + resultWindowH - highlightThickness);
        GL11.glVertex2f(resultWindowX + resultWindowW, resultWindowY + resultWindowH - highlightThickness);
        GL11.glVertex2f(resultWindowX + resultWindowW, resultWindowY + resultWindowH);
        GL11.glVertex2f(resultWindowX, resultWindowY + resultWindowH);
        GL11.glEnd();

        if (reel.isStopped) {
            GL11.glColor4f(hr, hg, hb, alphaMult * highlightAlpha * 0.6f);

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(resultWindowX, resultWindowY);
            GL11.glVertex2f(resultWindowX + highlightThickness, resultWindowY);
            GL11.glVertex2f(resultWindowX + highlightThickness, resultWindowY + resultWindowH);
            GL11.glVertex2f(resultWindowX, resultWindowY + resultWindowH);
            GL11.glEnd();

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(resultWindowX + resultWindowW - highlightThickness, resultWindowY);
            GL11.glVertex2f(resultWindowX + resultWindowW, resultWindowY);
            GL11.glVertex2f(resultWindowX + resultWindowW, resultWindowY + resultWindowH);
            GL11.glVertex2f(resultWindowX + resultWindowW - highlightThickness, resultWindowY + resultWindowH);
            GL11.glEnd();
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
            boolean allStopped = true;
            for (SlotReel reel : reels) {
                if (!reel.isStopped) {
                    allStopped = false;
                    break;
                }
            }

            if (allStopped) {
                float minRevealTime = 0.35f;
                boolean readyToComplete = true;
                for (SlotReel reel : reels) {
                    if (reel.revealTimer < minRevealTime) {
                        readyToComplete = false;
                        break;
                    }
                }

                if (readyToComplete) {
                    allRevealed = true;
                    for (GachaItem item : allItems) {
                        item.revealed = true;
                    }
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

        boolean allStopped = true;
        for (SlotReel reel : reels) {
            if (!reel.isStopped) {
                allStopped = false;
                break;
            }
        }

        if (allStopped) {
            allRevealed = true;
            for (GachaItem item : allItems) {
                item.revealed = true;
            }
        }
    }

    private void triggerRevealBurst(SlotReel reel) {
        float panelCenterX = p.getCenterX();
        float panelCenterY = p.getCenterY();
        float centerX = panelCenterX + reel.reelCenterX;

        activeRevealBursts.add(new RevealBurst(centerX, panelCenterY, reel.rarityColor));
        activeBursts.add(new ParticleBurst(centerX, panelCenterY, reel.rarityColor, 10 + reel.rarity * 3));
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
            reel.scrollOffset = 0f;
            reel.revealTimer = 0f;
            reel.showStars = false;
            reel.starAnimTimer = 0f;
            reel.populateVisibleShips();
        }
    }

    public boolean isAnimationComplete() {
        return allRevealed;
    }
}