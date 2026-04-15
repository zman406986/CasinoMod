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
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.util.Pair;

import data.scripts.casino.Strings;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.gacha.CasinoGachaManager.GachaData;

public class GachaAnimation extends BaseCustomUIPanelPlugin {

    // OpenGL Y=0 at BOTTOM, Y increases UPWARD. Increasing scrollOffset moves ships DOWNWARD visually.
    // targetListIndex: position in visibleHullIds where target ship resides. Decrements as reel shifts.
    // scrollOffset: controls ship vertical offset, also used for bounce animation (overshoot → 0).

    protected DialogCallbacks callbacks;
    protected CustomPanelAPI panel;
    protected PositionAPI p;

    protected List<GachaItem> allItems = new ArrayList<>();
    protected List<SlotReel> reels = new ArrayList<>();
    protected List<String> poolHullIds = new ArrayList<>();
    protected int nextReelToStop = 0;
    protected boolean allRevealed = false;
    protected float animationTimer = 0f;
    protected LabelAPI[] reelLabels;

    protected List<ParticleBurst> activeBursts = new ArrayList<>();
    protected List<RevealBurst> activeRevealBursts = new ArrayList<>();
    protected Map<String, SpriteAPI> spriteCache = new HashMap<>();
    protected Random random = new Random();
    
    protected List<ChasingLight> chasingLights = new ArrayList<>();
    protected float lightChaseTimer = 0f;
    protected int currentLightIndex = 0;
    protected SlotHandle slotHandle;
    protected LabelAPI footerLabel;
    protected LabelAPI pity5Label;
    protected LabelAPI pity4Label;
    protected GachaData gachaData;
    protected float reelsOffsetX = 0f;
    protected int visualPity5;
    protected int visualPity4;
    protected int reelsProcessedForPity = 0;
    protected boolean pityInitialized = false;
    protected static final float PITY_UPDATE_REVEAL_THRESHOLD = 0.35f;

    protected static final float WINDOW_HEIGHT = 420f;
    protected static final float SHIP_SLOT_HEIGHT = 70f;
    protected static final float FRAME_BORDER = 5f;
    protected static final float RESULT_WINDOW_PADDING = 4f;

    protected static final float SINGLE_REEL_WIDTH = 220f;
    protected static final float MULTI_REEL_WIDTH = 85f;
    protected static final float MULTI_REEL_SPACING = 5f;
    protected static final float LABEL_HEIGHT = 20f;

    protected static final float SCROLL_SPEED_BASE = 310f;
    protected static final float SCROLL_SPEED_VARIANCE = 30f;
    protected static final float STOP_DECEL_DURATION = 0.5f;
    protected static final int VISIBLE_SHIP_COUNT = 7;

    protected static final Color FRAME_COLOR = new Color(180, 150, 80);
    protected static final Color WINDOW_BG = new Color(35, 35, 50);
    protected static final Color CENTER_LINE_COLOR = new Color(255, 215, 0);
    protected static final Color BG_COLOR = new Color(25, 25, 35);
    
    protected static final float HEADER_HEIGHT = 55f;
    protected static final float FOOTER_HEIGHT = 38f;
    protected static final float CABINET_PADDING = 15f;
    
    protected static final float LIGHT_RADIUS = 5f;
    protected static final int LIGHTS_PER_SIDE = 10;
    protected static final float LIGHT_CHASE_SPEED = 0.08f;
    protected static final Color LIGHT_COLOR_OFF = new Color(60, 50, 30);
    protected static final Color LIGHT_COLOR_ON = new Color(255, 200, 50);
    protected static final Color LIGHT_COLOR_FLASH = new Color(255, 255, 150);
    
    protected static final float HANDLE_LENGTH = 70f;
    protected static final float HANDLE_BALL_RADIUS = 10f;
    protected static final float HANDLE_OFFSET = 20f;
    protected static final float HANDLE_PULL_ANGLE = 35f;
    protected static final float HANDLE_EXTENSION = HANDLE_OFFSET + HANDLE_LENGTH;

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

    private static void renderCircle(float cx, float cy, float radius, float r, float g, float b, float a, int segments) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_POLYGON);
        for (int i = 0; i < segments; i++) {
            float angle = (float)(i * 2 * Math.PI / segments);
            GL11.glVertex2f(cx + (float)Math.cos(angle) * radius, cy + (float)Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }

    private static Color brighten(Color c) {
        int r = Math.min(255, (int)(c.getRed() + (255 - c.getRed()) * 0.35f));
        int g = Math.min(255, (int)(c.getGreen() + (255 - c.getGreen()) * 0.35f));
        int b = Math.min(255, (int)(c.getBlue() + (255 - c.getBlue()) * 0.35f));
        return new Color(r, g, b, c.getAlpha());
    }

    private static Color darken(Color c) {
        int r = (int)(c.getRed() * 0.65f);
        int g = (int)(c.getGreen() * 0.65f);
        int b = (int)(c.getBlue() * 0.65f);
        return new Color(r, g, b, c.getAlpha());
    }

    private static void renderBeveledRect(float x, float y, float w, float h, Color baseColor, float thickness, float alphaMult) {
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

    private static void renderBevelBorder(float x, float y, float w, float h, float thickness, float alphaMult) {
        float[] lc = toGLComponents(brighten(FRAME_COLOR));
        float[] dc = toGLComponents(darken(FRAME_COLOR));

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

    private static void renderInnerShadow(float x, float y, float w, float h, float thickness, float alphaMult) {
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
        public boolean bouncePhase = false;
        public float bounceTimer = 0f;
        public float initialBounceOffset = 0f;
        public static final float OVERSHOOT_AMOUNT = 15f;
        public static final float BOUNCE_DURATION = 0.15f;
        public static final int SLOTS_TO_SCROLL = 5;
        public int shiftsRemaining;
        public int targetListIndex = -1;

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
                scrollOffset = initialBounceOffset * (1f - easeOut);
                
                if (progress >= 1f) {
                    scrollOffset = 0f;
                    bouncePhase = false;
                    isStopping = false;
                    isStopped = true;
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
                float scrollDistance = currentSpeed * amount;
                scrollOffset += scrollDistance;
                
                if (shiftsRemaining > 0) {
                    while (scrollOffset >= SHIP_SLOT_HEIGHT) {
                        scrollOffset -= SHIP_SLOT_HEIGHT;
                        shiftsRemaining--;
                        visibleHullIds.remove(0);
                        visibleHullIds.add(getRandomPoolHullId());
                        
                        if (targetListIndex > 0) {
                            targetListIndex--;
                        }
                    }
                }
                
                if (shiftsRemaining <= 0 && scrollOffset >= OVERSHOOT_AMOUNT) {
                    bouncePhase = true;
                    bounceTimer = 0f;
                    initialBounceOffset = scrollOffset;
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
            initialBounceOffset = 0f;
            
            targetListIndex = resultShipIndex + SLOTS_TO_SCROLL;
            if (targetListIndex < visibleHullIds.size()) {
                visibleHullIds.set(targetListIndex, resultHullId);
            }
            
            shiftsRemaining = SLOTS_TO_SCROLL;
            
            float totalDistance = SLOTS_TO_SCROLL * SHIP_SLOT_HEIGHT + OVERSHOOT_AMOUNT;
            stopDecelDuration = Math.max(0.5f, 3.5f * totalDistance / scrollSpeed);
        }

        public void instantStop() {
            isSpinning = false;
            isStopping = false;
            bouncePhase = false;
            isStopped = true;
            scrollOffset = 0f;
            initialBounceOffset = 0f;
            targetListIndex = resultShipIndex;
            visibleHullIds.set(resultShipIndex, resultHullId);
            revealTimer = 0.2f;
            showStars = true;
            starAnimTimer = 0.15f;
            triggerRevealBurst(this);
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
    
    public static class ChasingLight {
        public float offsetX, offsetY;
        public float radius;
        public int sequenceIndex;
        public float intensity;
        public boolean flashMode;
        public float flashTimer;
        
        public ChasingLight(float offsetX, float offsetY, int sequenceIndex) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.radius = LIGHT_RADIUS;
            this.sequenceIndex = sequenceIndex;
            this.intensity = 0f;
            this.flashMode = false;
            this.flashTimer = 0f;
        }
        
        public void setIntensity(float intensity) {
            this.intensity = Math.max(0f, Math.min(1f, intensity));
        }
        
        public void triggerFlash() {
            this.flashMode = true;
            this.flashTimer = 0f;
            this.intensity = 1f;
        }
        
        public boolean advance(float amount) {
            if (flashMode) {
                flashTimer += amount;
                if (flashTimer < 0.4f) {
                    intensity = 1f - (flashTimer / 0.4f) * 0.5f;
                } else if (flashTimer < 0.6f) {
                    float pulsePhase = (flashTimer - 0.4f) / 0.2f;
                    intensity = 0.5f + (float)Math.sin(pulsePhase * Math.PI) * 0.3f;
                } else {
                    flashMode = false;
                    intensity = 0f;
                }
            }
            return true;
        }
        
        public void render(float cx, float cy, float alphaMult) {
            float x = cx + offsetX;
            float y = cy + offsetY;
            
            Color baseColor = flashMode ? LIGHT_COLOR_FLASH : LIGHT_COLOR_ON;
            float[] c = toGLComponents(baseColor);
            
            if (intensity > 0.1f) {
                float glowRadius = radius * 2.5f;
                float glowAlpha = intensity * alphaMult * 0.3f;
                GachaAnimation.renderCircle(x, y, glowRadius, c[0], c[1], c[2], glowAlpha, 8);
            }
            
            float[] offC = toGLComponents(LIGHT_COLOR_OFF);
            float litRatio = intensity;
            float r = offC[0] + (c[0] - offC[0]) * litRatio;
            float g = offC[1] + (c[1] - offC[1]) * litRatio;
            float b = offC[2] + (c[2] - offC[2]) * litRatio;
            
            GachaAnimation.renderCircle(x, y, radius, r, g, b, alphaMult, 8);
        }
    }
    
    public static class SlotHandle {
        public float offsetX;
        public float offsetY;
        public float currentAngle;
        public float targetAngle;
        public float animTimer;
        public State state;
        public int pendingPulls;
        
        protected static final float ANIM_DURATION = 0.2f;
        
        public enum State { IDLE, PULLING, RETURNING }
        
        public SlotHandle(float offsetX, float offsetY) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.currentAngle = 0f;
            this.targetAngle = 0f;
            this.animTimer = 0f;
            this.state = State.IDLE;
            this.pendingPulls = 0;
        }
        
        public void triggerPull() {
            pendingPulls++;
        }
        
        public void advance(float amount) {
            if (pendingPulls > 0 && state == State.IDLE) {
                pendingPulls--;
                state = State.PULLING;
                targetAngle = HANDLE_PULL_ANGLE;
                animTimer = 0f;
            }
            
            if (state == State.PULLING) {
                animTimer += amount;
                float progress = Math.min(1f, animTimer / ANIM_DURATION);
                currentAngle = HANDLE_PULL_ANGLE * progress;
                
                if (animTimer >= ANIM_DURATION) {
                    currentAngle = HANDLE_PULL_ANGLE;
                    state = State.RETURNING;
                    targetAngle = 0f;
                    animTimer = 0f;
                }
            } else if (state == State.RETURNING) {
                animTimer += amount;
                float progress = Math.min(1f, animTimer / ANIM_DURATION);
                currentAngle = HANDLE_PULL_ANGLE * (1f - progress);
                
                if (animTimer >= ANIM_DURATION) {
                    currentAngle = 0f;
                    state = State.IDLE;
                    animTimer = 0f;
                }
            }
        }
        
        public void render(float baseX, float baseY, float alphaMult) {
            float x = baseX + offsetX;
            float y = baseY + offsetY;
            
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            
            float[] fc = toGLComponents(FRAME_COLOR);
            float handleEndX = x + (float)Math.cos(Math.toRadians(-90 + currentAngle)) * HANDLE_LENGTH;
            float handleEndY = y + (float)Math.sin(Math.toRadians(-90 + currentAngle)) * HANDLE_LENGTH;
            
            float leverWidth = 6f;
            float dx = handleEndX - x;
            float dy = handleEndY - y;
            float len = (float)Math.sqrt(dx * dx + dy * dy);
            float nx = -dy / len * leverWidth / 2f;
            float ny = dx / len * leverWidth / 2f;
            
            GL11.glColor4f(fc[0], fc[1], fc[2], alphaMult * 0.8f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(x + nx, y + ny);
            GL11.glVertex2f(x - nx, y - ny);
            GL11.glVertex2f(handleEndX - nx, handleEndY - ny);
            GL11.glVertex2f(handleEndX + nx, handleEndY + ny);
            GL11.glEnd();
            
            float[] bc = toGLComponents(LIGHT_COLOR_ON);
            if (state == State.PULLING) {
                bc = toGLComponents(new Color(255, 255, 200));
            }
            
            float ballGlow = HANDLE_BALL_RADIUS * 1.8f;
            GachaAnimation.renderCircle(handleEndX, handleEndY, ballGlow, bc[0], bc[1], bc[2], alphaMult * 0.25f, 12);
            GachaAnimation.renderCircle(handleEndX, handleEndY, HANDLE_BALL_RADIUS, bc[0], bc[1], bc[2], alphaMult, 12);
            
            float pivotRadius = 8f;
            GachaAnimation.renderCircle(x, y, pivotRadius, fc[0], fc[1], fc[2], alphaMult, 12);
            
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
    
    public void setInitialPity(int pity5, int pity4) {
        this.visualPity5 = pity5;
        this.visualPity4 = pity4;
        this.reelsProcessedForPity = 0;
        this.pityInitialized = true;
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
        
        try {
            gachaData = new CasinoGachaManager().getData();
        } catch (Exception e) {
            gachaData = null;
        }
        
        if (!pityInitialized && gachaData != null) {
            visualPity5 = gachaData.pity5;
            visualPity4 = gachaData.pity4;
        }
        
        createReels();
        createSlotHandle();
        createChasingLights();
        createReelLabels();
        createPityLabels();
        createFooterLabel();
    }
    
    private void createChasingLights() {
        if (reels.isEmpty()) return;
        
        int numReels = reels.size();
        boolean isSingle = numReels == 1;
        float reelWidth = isSingle ? SINGLE_REEL_WIDTH : MULTI_REEL_WIDTH;
        float spacing = isSingle ? 0f : MULTI_REEL_SPACING;
        float totalReelWidth = numReels * reelWidth + (numReels - 1) * spacing;
        
        float cabinetPadding = isSingle ? CABINET_PADDING : 5f;
        float handleExt = isSingle ? HANDLE_EXTENSION : 15f;
        
        float halfWidth = totalReelWidth / 2f + FRAME_BORDER + cabinetPadding + handleExt / 2f;
        float halfHeight = WINDOW_HEIGHT / 2f + FRAME_BORDER + (HEADER_HEIGHT + FOOTER_HEIGHT) / 2f;
        
        float lightOuterOffset = LIGHT_RADIUS + 5f;
        
        int index = 0;
        
        for (int i = 0; i < LIGHTS_PER_SIDE; i++) {
            float xOffset = -halfWidth + (i + 0.5f) * (2f * halfWidth / LIGHTS_PER_SIDE) + reelsOffsetX;
            chasingLights.add(new ChasingLight(xOffset, halfHeight + lightOuterOffset, index++));
        }
        
        for (int i = 0; i < LIGHTS_PER_SIDE; i++) {
            float xOffset = -halfWidth + (i + 0.5f) * (2f * halfWidth / LIGHTS_PER_SIDE) + reelsOffsetX;
            chasingLights.add(new ChasingLight(xOffset, -halfHeight - lightOuterOffset, index++));
        }
        
        for (int i = 0; i < LIGHTS_PER_SIDE; i++) {
            float yOffset = -halfHeight + (i + 0.5f) * (2f * halfHeight / LIGHTS_PER_SIDE);
            chasingLights.add(new ChasingLight(-halfWidth - lightOuterOffset + reelsOffsetX, yOffset, index++));
        }
        
        for (int i = 0; i < LIGHTS_PER_SIDE; i++) {
            float yOffset = -halfHeight + (i + 0.5f) * (2f * halfHeight / LIGHTS_PER_SIDE);
            chasingLights.add(new ChasingLight(halfWidth + lightOuterOffset + reelsOffsetX, yOffset, index++));
        }
    }
    
    private void createSlotHandle() {
        if (reels.isEmpty()) return;
        
        int numReels = reels.size();
        boolean isSingle = numReels == 1;
        float reelWidth = isSingle ? SINGLE_REEL_WIDTH : MULTI_REEL_WIDTH;
        float spacing = isSingle ? 0f : MULTI_REEL_SPACING;
        float totalReelWidth = numReels * reelWidth + (numReels - 1) * spacing;
        
        float cabinetPadding = isSingle ? CABINET_PADDING : 5f;
        float handleOffsetX = totalReelWidth / 2f + FRAME_BORDER + HANDLE_OFFSET + cabinetPadding + reelsOffsetX;
        float handleOffsetY = 0f;
        slotHandle = new SlotHandle(handleOffsetX, handleOffsetY);
    }
    
    private void createFooterLabel() {
        if (panel == null) return;
        
        float panelY = panel.getPosition().getY();
        float panelH = panel.getPosition().getHeight();
        float panelCenterY = panel.getPosition().getCenterY();
        
        float footerGLY = panelCenterY - WINDOW_HEIGHT / 2f - FRAME_BORDER - 35f;
        float footerUIY = (panelY + panelH) - footerGLY;
        
        footerLabel = Global.getSettings().createLabel("", Fonts.DEFAULT_SMALL);
        footerLabel.setAlignment(Alignment.MID);
        footerLabel.setColor(new Color(150, 150, 150));
        float labelW = panel.getPosition().getWidth() - 100f;
        panel.addComponent((UIComponentAPI) footerLabel).inTL(50f, footerUIY).setSize(labelW, 20f);
    }
    
    private void createPityLabels() {
        if (panel == null) return;
        
        int numReels = reels.size();
        boolean isSingle = numReels == 1;
        float reelWidth = isSingle ? SINGLE_REEL_WIDTH : MULTI_REEL_WIDTH;
        float spacing = isSingle ? 0f : MULTI_REEL_SPACING;
        float totalReelWidth = numReels * reelWidth + (numReels - 1) * spacing;
        
        float panelY = panel.getPosition().getY();
        float panelH = panel.getPosition().getHeight();
        float panelCenterX = panel.getPosition().getCenterX();
        float panelCenterY = panel.getPosition().getCenterY();
        float panelX = panel.getPosition().getX();
        
        float headerGLY = panelCenterY + WINDOW_HEIGHT / 2f + FRAME_BORDER + HEADER_HEIGHT * 0.65f;
        float headerUIY = (panelY + panelH) - headerGLY;
        
        float pity5BarX = panelCenterX + totalReelWidth / 4f + 20f + reelsOffsetX - panelX;
        float pity4BarX = pity5BarX + 80f + 10f;
        
        pity5Label = Global.getSettings().createLabel("", Fonts.DEFAULT_SMALL);
        pity5Label.setAlignment(Alignment.MID);
        pity5Label.setColor(new Color(100, 180, 255));
        panel.addComponent((UIComponentAPI) pity5Label).inTL(pity5BarX, headerUIY).setSize(80f, 14f);
        
        pity4Label = Global.getSettings().createLabel("", Fonts.DEFAULT_SMALL);
        pity4Label.setAlignment(Alignment.MID);
        pity4Label.setColor(new Color(180, 100, 255));
        panel.addComponent((UIComponentAPI) pity4Label).inTL(pity4BarX, headerUIY).setSize(60f, 14f);
    }

    private void createReelLabels() {
        if (panel == null || reels.isEmpty()) return;
        
        int numReels = reels.size();
        boolean isSingle = numReels == 1;
        float reelWidth = isSingle ? SINGLE_REEL_WIDTH : MULTI_REEL_WIDTH;
        
        float panelY = panel.getPosition().getY();
        float panelH = panel.getPosition().getHeight();
        float panelX = panel.getPosition().getX();
        float panelCenterX = panel.getPosition().getCenterX();
        float panelCenterY = panel.getPosition().getCenterY();
        
        float labelGLY = panelCenterY - WINDOW_HEIGHT / 2f - FRAME_BORDER - FOOTER_HEIGHT + LABEL_HEIGHT / 2f + 6f;
        float labelUIY = (panelY + panelH) - labelGLY;
        
        reelLabels = new LabelAPI[numReels];
        
        for (int i = 0; i < numReels; i++) {
            SlotReel reel = reels.get(i);
            float labelX = panelCenterX + reel.reelCenterX - reelWidth / 2f - panelX;
            
            LabelAPI lbl = Global.getSettings().createLabel("", Fonts.DEFAULT_SMALL);
            lbl.setAlignment(Alignment.MID);
            lbl.setColor(CENTER_LINE_COLOR);
            panel.addComponent((UIComponentAPI) lbl).inTL(labelX, labelUIY).setSize(reelWidth, LABEL_HEIGHT);
            reelLabels[i] = lbl;
        }
    }

    private void createReels() {
        int numReels = allItems.size();
        boolean isSingle = numReels == 1;

        float reelWidth = isSingle ? SINGLE_REEL_WIDTH : MULTI_REEL_WIDTH;
        float spacing = isSingle ? 0f : MULTI_REEL_SPACING;
        float totalWidth = numReels * reelWidth + (numReels - 1) * spacing;
        
        float handleExt = isSingle ? HANDLE_EXTENSION : 15f;
        reelsOffsetX = -handleExt / 2f;
        float startX = -totalWidth / 2f + reelsOffsetX;

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

        int numReels = reels.size();
        boolean isSingle = numReels == 1;
        float reelWidth = isSingle ? SINGLE_REEL_WIDTH : MULTI_REEL_WIDTH;
        float spacing = isSingle ? 0f : MULTI_REEL_SPACING;
        float totalReelWidth = numReels * reelWidth + (numReels - 1) * spacing;
        
        float cabinetPadding = isSingle ? CABINET_PADDING : 5f;
        float handleExt = isSingle ? HANDLE_EXTENSION : 15f;
        
        float cabinetLeft = panelCenterX - totalReelWidth / 2f - FRAME_BORDER - cabinetPadding + reelsOffsetX;
        float cabinetRight = panelCenterX + totalReelWidth / 2f + FRAME_BORDER + cabinetPadding + handleExt + reelsOffsetX;
        float cabinetTop = panelCenterY + WINDOW_HEIGHT / 2f + FRAME_BORDER + HEADER_HEIGHT;
        float cabinetBottom = panelCenterY - WINDOW_HEIGHT / 2f - FRAME_BORDER - FOOTER_HEIGHT;
        
        renderCabinetFrame(panelCenterX + reelsOffsetX, panelCenterY, cabinetLeft, cabinetRight, cabinetTop, cabinetBottom, totalReelWidth, alphaMult);
        
        float cabinetCenterX = (cabinetLeft + cabinetRight) / 2f;
        float cabinetCenterY = (cabinetTop + cabinetBottom) / 2f;

        for (ChasingLight light : chasingLights) {
            light.render(cabinetCenterX, cabinetCenterY, alphaMult);
        }
        
        float windowBottom = panelCenterY - WINDOW_HEIGHT / 2f;

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

        if (slotHandle != null) {
            slotHandle.render(panelCenterX, panelCenterY, alphaMult);
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        for (ParticleBurst burst : activeBursts) {
            burst.render(alphaMult);
        }
        for (RevealBurst burst : activeRevealBursts) {
            burst.render(alphaMult);
        }
    }
    
    private void renderCabinetFrame(float panelCenterX, float panelCenterY, float left, float right, float top, float bottom, float totalReelWidth, float alphaMult) {
        Color cabinetBg = new Color(30, 25, 40);
        renderColorQuad(left, bottom, right - left, top - bottom, cabinetBg, alphaMult);
        
        Color frameBg = new Color(50, 40, 60);
        float borderW = 4f;
        renderBeveledRect(left, bottom, right - left, top - bottom, frameBg, 2f, alphaMult);
        
        Color headerBgTop = new Color(60, 45, 70);
        Color headerBgBottom = new Color(40, 30, 50);
        float headerY = panelCenterY + WINDOW_HEIGHT / 2f + FRAME_BORDER;
        renderGradientQuad(left + borderW, headerY, right - left - borderW * 2, HEADER_HEIGHT - borderW, headerBgTop, headerBgBottom, alphaMult);
        
        float pityBarW = 80f;
        float pityBarH = 12f;
        float pityBarX = panelCenterX + totalReelWidth / 4f + 20f;
        float pityBarY = headerY + HEADER_HEIGHT * 0.65f;
        
        Color pityBg = new Color(20, 20, 30);
        Color pityBorder = new Color(35, 30, 45);
        renderBeveledRect(pityBarX, pityBarY, pityBarW, pityBarH, pityBorder, 1f, alphaMult);
        renderColorQuad(pityBarX + 1f, pityBarY + 1f, pityBarW - 2f, pityBarH - 2f, pityBg, alphaMult);
        
        float pity5Progress = (float) visualPity5 / CasinoConfig.PITY_HARD_5;
        float filledW = Math.max(0f, pityBarW - 2f) * pity5Progress;
        Color pityColor = pity5Progress > 0.8f ? new Color(255, 100, 50) : new Color(100, 180, 255);
        if (filledW > 0f) {
            renderColorQuad(pityBarX + 1f, pityBarY + 1f, filledW, pityBarH - 2f, pityColor, alphaMult);
        }
        
        float pity4BarW = 60f;
        float pity4BarX = pityBarX + pityBarW + 10f;
        float pity4Progress = (float) visualPity4 / CasinoConfig.PITY_HARD_4;
        float pity4FilledW = Math.max(0f, pity4BarW - 2f) * pity4Progress;
        Color pity4Color = pity4Progress > 0.8f ? new Color(255, 150, 100) : new Color(180, 100, 255);
        renderBeveledRect(pity4BarX, pityBarY, pity4BarW, pityBarH, pityBorder, 1f, alphaMult);
        renderColorQuad(pity4BarX + 1f, pityBarY + 1f, pity4BarW - 2f, pityBarH - 2f, pityBg, alphaMult);
        if (pity4FilledW > 0f) {
            renderColorQuad(pity4BarX + 1f, pityBarY + 1f, pity4FilledW, pityBarH - 2f, pity4Color, alphaMult);
        }
        
        float footerY = bottom + borderW;
        Color footerBg = new Color(25, 20, 35);
        renderColorQuad(left + borderW, footerY, right - left - borderW * 2, FOOTER_HEIGHT - borderW, footerBg, alphaMult);
    }
    
    private void renderGradientQuad(float x, float y, float w, float h, Color topColor, Color bottomColor, float alphaMult) {
        float[] tc = toGLComponents(topColor);
        float[] bc = toGLComponents(bottomColor);
        
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(tc[0], tc[1], tc[2], alphaMult);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glColor4f(bc[0], bc[1], bc[2], alphaMult);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
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
            float shipCenterY = windowCenterY + (i - reel.resultShipIndex) * SHIP_SLOT_HEIGHT - reel.scrollOffset;
            float distanceFromCenter = Math.abs(shipCenterY - windowCenterY);
            
            if (distanceFromCenter > windowHalfHeight + SHIP_SLOT_HEIGHT) continue;

            float fadeAlpha = Math.max(0.7f, 1f - Math.min(1f, distanceFromCenter / windowHalfHeight)) * alphaMult;

            String hullId = reel.visibleHullIds.get(i);
            SpriteAPI sprite = getShipSprite(hullId);
            renderShipSprite(sprite, reelCenterX, shipCenterY, maxShipWidth, maxShipHeight, fadeAlpha);
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
        float border = FRAME_BORDER;

        float bevelThickness = 1.5f;
        renderBevelBorder(reelLeft, windowBottom - border, reel.reelWidth, WINDOW_HEIGHT + border * 2, bevelThickness, alphaMult);

        float windowCenterY = windowBottom + WINDOW_HEIGHT / 2f;
        float resultWindowH = SHIP_SLOT_HEIGHT + RESULT_WINDOW_PADDING * 2;
        float resultWindowY = windowCenterY - resultWindowH / 2f;
        float resultWindowW = reel.reelWidth - border * 2;
        float resultWindowX = reelLeft + border;

        float innerShadowThickness = 2f;
        renderInnerShadow(resultWindowX, resultWindowY, resultWindowW, resultWindowH, innerShadowThickness, alphaMult);

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

        updatePityForRevealedReels();
        updateReelLabels();
        updateChasingLights(amount);
        updateFooterLabel();
        updatePityLabels();
        
        if (slotHandle != null) {
            slotHandle.advance(amount);
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
    
    private void updatePityForRevealedReels() {
        while (reelsProcessedForPity < reels.size()) {
            SlotReel reel = reels.get(reelsProcessedForPity);
            if (reel.isStopped && reel.revealTimer >= PITY_UPDATE_REVEAL_THRESHOLD) {
                visualPity5++;
                visualPity4++;
                if (reel.rarity == 5) {
                    visualPity5 = 0;
                }
                if (reel.rarity == 4) {
                    visualPity4 = 0;
                }
                reelsProcessedForPity++;
            } else {
                break;
            }
        }
    }
    
    private void updateChasingLights(float amount) {
        boolean anySpinning = reels.stream().anyMatch(r -> r.isSpinning);
        
        lightChaseTimer += amount;
        
        float chaseSpeed = anySpinning ? LIGHT_CHASE_SPEED : LIGHT_CHASE_SPEED * 3f;
        float baseIntensity = anySpinning ? 1f : 0.5f;
        float neighborIntensity = anySpinning ? 0.6f : 0.25f;
        updateLightIntensities(amount, chaseSpeed, baseIntensity, neighborIntensity);
        
        for (SlotReel reel : reels) {
            if (reel.isStopped && reel.revealTimer < 0.6f && reel.revealTimer > 0f) {
                triggerLightFlash();
            }
        }
    }
    
    private void updateLightIntensities(float amount, float chaseSpeed, float baseIntensity, float neighborIntensity) {
        if (lightChaseTimer >= chaseSpeed) {
            lightChaseTimer = 0f;
            currentLightIndex = (currentLightIndex + 1) % chasingLights.size();
        }
        
        int totalLights = chasingLights.size();
        for (int i = 0; i < totalLights; i++) {
            ChasingLight light = chasingLights.get(i);
            int dist = Math.abs(i - currentLightIndex);
            if (dist > totalLights / 2) dist = totalLights - dist;
            
            float intensity = dist == 0 ? baseIntensity : dist == 1 ? neighborIntensity : dist == 2 ? baseIntensity * 0.25f : 0f;
            light.setIntensity(intensity);
            light.advance(amount);
        }
    }
    
    private void triggerLightFlash() {
        for (ChasingLight light : chasingLights) {
            if (!light.flashMode) {
                light.triggerFlash();
            }
        }
    }
    
    private void updateFooterLabel() {
        if (footerLabel == null) return;
        
        if (allRevealed) {
            footerLabel.setText(Strings.get("gacha_slot.click_continue"));
            footerLabel.setColor(new Color(100, 200, 100));
        } else if (slotHandle != null && slotHandle.state == SlotHandle.State.IDLE && !allReelsStopped()) {
            footerLabel.setText(Strings.get("gacha_slot.click_stop"));
            footerLabel.setColor(new Color(150, 150, 150));
        } else {
            footerLabel.setText("");
        }
    }
    
    private void updatePityLabels() {
        if (pity5Label != null) {
            pity5Label.setText(visualPity5 + "/" + CasinoConfig.PITY_HARD_5);
            float progress = (float) visualPity5 / CasinoConfig.PITY_HARD_5;
            if (progress > 0.8f) {
                pity5Label.setColor(new Color(255, 100, 50));
            } else {
                pity5Label.setColor(new Color(100, 180, 255));
            }
        }
        
        if (pity4Label != null) {
            pity4Label.setText(visualPity4 + "/" + CasinoConfig.PITY_HARD_4);
            float progress = (float) visualPity4 / CasinoConfig.PITY_HARD_4;
            if (progress > 0.8f) {
                pity4Label.setColor(new Color(255, 150, 100));
            } else {
                pity4Label.setColor(new Color(180, 100, 255));
            }
        }
    }

    private void updateReelLabels() {
        if (reelLabels == null) return;
        
        for (int i = 0; i < reels.size(); i++) {
            SlotReel reel = reels.get(i);
            LabelAPI lbl = reelLabels[i];
            
            if (reel.isStopped) {
                lbl.setText("");
            } else if (reel.isStopping) {
                lbl.setText("Stopping...");
                lbl.setColor(Color.YELLOW);
            } else if (i == nextReelToStop) {
                lbl.setText("Click to stop");
                lbl.setColor(CENTER_LINE_COLOR);
            } else {
                lbl.setText("");
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
        if (slotHandle != null && !allRevealed) {
            slotHandle.triggerPull();
        }
        
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
        
        reelsProcessedForPity = 0;
        pityInitialized = false;
        
        lightChaseTimer = 0f;
        currentLightIndex = 0;
        for (ChasingLight light : chasingLights) {
            light.intensity = 0f;
            light.flashMode = false;
            light.flashTimer = 0f;
        }
        
        if (slotHandle != null) {
            slotHandle.currentAngle = 0f;
            slotHandle.targetAngle = 0f;
            slotHandle.animTimer = 0f;
            slotHandle.state = SlotHandle.State.IDLE;
            slotHandle.pendingPulls = 0;
        }
        
        try {
            gachaData = new CasinoGachaManager().getData();
        } catch (Exception e) {
            gachaData = null;
        }

        for (SlotReel reel : reels) {
            reel.isSpinning = true;
            reel.isStopping = false;
            reel.isStopped = false;
            reel.bouncePhase = false;
            reel.scrollOffset = 0f;
            reel.initialBounceOffset = 0f;
            reel.shiftsRemaining = 0;
            reel.revealTimer = 0f;
            reel.showStars = false;
            reel.starAnimTimer = 0f;
            reel.targetListIndex = -1;
            reel.populateVisibleShips();
        }
        
        updateReelLabels();
        updateFooterLabel();
        updatePityLabels();
    }
}