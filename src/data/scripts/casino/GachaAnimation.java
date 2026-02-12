package data.scripts.casino;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.input.Keyboard;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;

public class GachaAnimation extends BaseCustomUIPanelPlugin {

    protected InteractionDialogAPI dialog;
    protected DialogCallbacks callbacks;
    protected CustomPanelAPI panel;
    protected PositionAPI p;

    // Animation state
    protected List<GachaItem> allItems = new ArrayList<>();
    protected List<GachaItem> revealedItems = new ArrayList<>();
    protected int currentRevealIndex = 0;
    protected boolean waitingForClick = false;
    protected static final float SPIN_DURATION = 3f; // 3 seconds for 3 rotations

    protected boolean animationRunning = false;
    protected boolean animationComplete = false;
    protected float animationTimer = 0f;

    // Visual elements
    protected FaderUtil spinnerFader = new FaderUtil(0f, 1f, 0.5f, true, true);
    protected SpriteAPI backgroundSprite;
    protected float centerX, centerY;

    // Particle burst effects for reveal
    protected List<ParticleBurst> activeBursts = new ArrayList<>();

    // Layout constants
    protected static final int COLUMNS = 2;
    protected static final float ITEM_WIDTH = 120f;
    protected static final float ITEM_HEIGHT = 60f;
    protected static final float COLUMN_SPACING = 200f;
    protected static final float ROW_SPACING = 80f;

    // Close button
    protected ButtonAPI closeButton;
    protected static final float CLOSE_BUTTON_W = 80f;
    protected static final float CLOSE_BUTTON_H = 30f;

    // Callback for when animation completes
    protected GachaAnimationCallback callback;

    public interface GachaAnimationCallback {
        void onAnimationComplete(List<GachaItem> results);
    }

    public static class GachaItem {
        public String id;
        public String name;
        public String hullId;
        public String weaponId;
        public int rarity;
        public Color color;
        public boolean revealed = false;
        public float spinTimer = 0f; // Timer for spinning phase
        public boolean isFixed = false; // Whether item has stopped spinning and is fixed/bright

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

        public void setWeaponId(String weaponId) {
            this.weaponId = weaponId;
        }



        // Get spin speed based on rarity
        public float getSpinSpeed() {
            return switch(rarity) {
                case 5 -> 12f;
                case 4 -> 9f;
                case 3 -> 7f;
                default -> 6f;
            };
        }

        // Get dark color for spinning phase (brighter for visibility against dark bg)
        public Color getDarkColor() {
            return new Color(
                (int)(color.getRed() * 0.7f),
                (int)(color.getGreen() * 0.7f),
                (int)(color.getBlue() * 0.7f)
            );
        }

        // Get background alpha based on rarity (higher for visibility against dark bg)
        public float getBackgroundAlpha() {
            return switch(rarity) {
                case 5 -> 0.95f;
                case 4 -> 0.9f;
                case 3 -> 0.85f;
                case 2 -> 0.8f;
                default -> 0.75f;
            };
        }
    }

    // Particle burst effect class for reveal
    public static class ParticleBurst {
        public float x, y;
        public Color color;
        public float lifetime;
        public float maxLifetime;
        public List<Pair<Float, Float>> particles; // angle and speed pairs

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
            float progress = lifetime / maxLifetime;
            float alpha = (1f - progress) * alphaMult;

            int particleIndex = 0;
            for (Pair<Float, Float> particle : particles) {
                float angle = particle.one;
                float speed = particle.two;
                float dist = speed * lifetime;
                float px = x + (float)Math.cos(angle) * dist;
                float py = y + (float)Math.sin(angle) * dist;

                float size = 3f + (particleIndex % 3) * 2f;
                size *= (1f - progress * 0.5f);

                if (particleIndex % 2 == 0) {
                    Misc.renderQuad(px - size/2, py - size/2, size, size, color, alpha);
                } else {
                    float halfSize = size / 2f;
                    float quarterSize = size / 4f;
                    Misc.renderQuad(px - quarterSize, py - halfSize, quarterSize * 2, size, color, alpha);
                    Misc.renderQuad(px - halfSize, py - quarterSize, size, quarterSize * 2, color, alpha);
                }

                particleIndex++;
            }
        }
    }

    public GachaAnimation(List<GachaItem> itemsToAnimate, GachaAnimationCallback callback) {
        this.allItems.addAll(itemsToAnimate);
        this.callback = callback;
        spinnerFader.fadeIn();
    }

    public void init(CustomPanelAPI panel, DialogCallbacks callbacks, InteractionDialogAPI dialog) {
        this.panel = panel;
        this.callbacks = callbacks;
        this.dialog = dialog;

        try {
            backgroundSprite = Global.getSettings().getSprite("graphics/campaign/map/screenFlash2.png");
        } catch (Exception e) {
            // Background not found, that's okay
        }

        // Create close button using UI element
        createCloseButton();
    }

    protected void createCloseButton() {
        if (panel == null) return;

        // Create a small panel for the close button
        CustomPanelAPI buttonPanel = panel.createCustomPanel(CLOSE_BUTTON_W, CLOSE_BUTTON_H, null);
        TooltipMakerAPI tooltip = buttonPanel.createUIElement(CLOSE_BUTTON_W, CLOSE_BUTTON_H, false);

        // Add the skip button
        closeButton = tooltip.addButton("Skip", "skip", CLOSE_BUTTON_W, CLOSE_BUTTON_H, 0f);
        closeButton.getPosition().inTL(0f, 0f);

        buttonPanel.addUIElement(tooltip).inTL(0f, 0f);
        panel.addComponent(buttonPanel).inTR(10f, 10f);
    }

    public void positionChanged(PositionAPI position) {
        this.p = position;
        if (p != null) {
            centerX = p.getCenterX();
            centerY = p.getCenterY();
        }
    }

    public void renderBelow(float alphaMult) {
        if (p == null) return;

        float x = p.getX();
        float y = p.getY();
        float w = p.getWidth();
        float h = p.getHeight();

        // Enable scissor test to clip rendering to panel boundaries
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        float s = Global.getSettings().getScreenScaleMult();
        GL11.glScissor((int)(x * s), (int)(y * s), (int)(w * s), (int)(h * s));

        // Draw background - use a darker base color for better contrast
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Misc.renderQuad(x, y, w, h, new Color(15, 15, 20), alphaMult);

        // Draw background effect with lower alpha so it doesn't brighten too much
        if (backgroundSprite != null) {
            backgroundSprite.setSize(w, h);
            backgroundSprite.setAlphaMult(alphaMult * 0.3f);
            backgroundSprite.render(x, y);
        }

        // Draw all items in multi-column layout
        drawRevealedItems(alphaMult);

        // Draw particle bursts
        for (ParticleBurst burst : activeBursts) {
            burst.render(alphaMult);
        }

        // Disable scissor test after rendering
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public void render(float alphaMult) {
        // All rendering is done in renderBelow() for proper layering
        // This follows the pattern used by DuelPanel
    }


    private void drawRevealedItems(float alphaMult) {
        if (revealedItems.isEmpty()) return;

        int itemsPerColumn = (allItems.size() + 1) / COLUMNS;
        float startX = centerX - COLUMN_SPACING / 2f - ITEM_WIDTH / 2f;
        float startY = centerY + (itemsPerColumn - 1) * ROW_SPACING / 2f;

        for (int i = 0; i < revealedItems.size(); i++) {
            GachaItem item = revealedItems.get(i);

            int col = i / itemsPerColumn;
            int row = i % itemsPerColumn;

            float itemX = startX + col * COLUMN_SPACING;
            float itemY = startY - row * ROW_SPACING;

            // Calculate spinning rotation for the item
            float rotation = 0f;
            if (!item.isFixed) {
                // Still spinning - calculate rotation based on spinTimer
                float spinSpeed = item.getSpinSpeed();
                rotation = item.spinTimer * spinSpeed;
            }

            drawItem(item, itemX, itemY, rotation, alphaMult);
        }

        // Draw particle bursts
        for (ParticleBurst burst : activeBursts) {
            burst.render(alphaMult);
        }
    }

    private void drawItem(GachaItem item, float x, float y, float rotation, float alphaMult) {
        float w = ITEM_WIDTH;
        float h = ITEM_HEIGHT;

        // Calculate reveal scale animation
        float revealScale = 1f;
        if (item.isFixed && item.spinTimer <= SPIN_DURATION + 0.3f) {
            float revealProgress = (item.spinTimer - SPIN_DURATION) / 0.3f;
            if (revealProgress < 1f) {
                revealScale = 0.5f + 0.5f * revealProgress;
            }
        }

        // Determine colors based on phase
        Color displayColor;
        Color borderColor;
        float bgAlpha;

        if (!item.isFixed) {
            displayColor = item.getDarkColor();
            borderColor = new Color(150, 150, 150);
            bgAlpha = 0.4f;
        } else {
            displayColor = item.color;
            borderColor = item.rarity >= 4 ? new Color(255, 215, 0) : Color.WHITE;
            bgAlpha = item.getBackgroundAlpha();
        }

        if (!item.isFixed) {
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0);
            GL11.glRotatef((float)Math.toDegrees(rotation), 0, 0, 1);
            GL11.glTranslatef(-x, -y, 0);
        } else if (revealScale != 1f) {
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0);
            GL11.glScalef(revealScale, revealScale, 1f);
            GL11.glTranslatef(-x, -y, 0);
        }

        // Draw glow effect for higher rarity items
        if (item.isFixed && item.rarity >= 3) {
            drawItemGlow(item, x, y, w, h, alphaMult);
        }

        // Draw star-shaped item background
        drawStarShape(x, y, w, h, displayColor, alphaMult * bgAlpha);

        // Draw star-shaped border
        drawStarShapeBorder(x, y, w, h, borderColor, alphaMult);

        if (!item.isFixed) {
            GL11.glPopMatrix();
        } else if (revealScale != 1f) {
            GL11.glPopMatrix();
        }

        // Draw decorative particles for 3*, 4* and 5* items only when fixed
        if (item.isFixed && item.rarity >= 3) {
            drawItemRarityEffect(item, x, y, alphaMult);
        }

        // Draw stars only when item is fixed (bright phase)
        if (item.isFixed) {
            drawStars(item, x, y + h/2 + 18, alphaMult);
        }
    }

    private void drawItemGlow(GachaItem item, float x, float y, float w, float h, float alphaMult) {
        float glowSize = 1.2f + (item.rarity - 3) * 0.15f;
        float glowW = w * glowSize;
        float glowH = h * glowSize;
        float glowAlpha = 0.3f + (item.rarity - 3) * 0.1f;

        Color glowColor = new Color(
            item.color.getRed(),
            item.color.getGreen(),
            item.color.getBlue(),
            (int)(255 * glowAlpha * alphaMult)
        );

        drawStarShape(x, y, glowW, glowH, glowColor, alphaMult);
    }

    private void drawStarShapeBorder(float x, float y, float width, float height, Color color, float alphaMult) {
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

    private void drawItemRarityEffect(GachaItem item, float x, float y, float alphaMult) {
        Color effectColor;
        int numParticles;
        float baseRadius;
        float particleSize;
        float rotationSpeed;

        if (item.rarity == 5) {
            effectColor = new Color(255, 215, 0, (int)(180 * alphaMult));
            numParticles = 8;
            baseRadius = 50f;
            particleSize = 6f;
            rotationSpeed = 1.5f;
        } else if (item.rarity == 4) {
            effectColor = new Color(200, 50, 255, (int)(180 * alphaMult));
            numParticles = 6;
            baseRadius = 40f;
            particleSize = 5f;
            rotationSpeed = 1.2f;
        } else {
            effectColor = new Color(100, 150, 255, (int)(160 * alphaMult));
            numParticles = 4;
            baseRadius = 32f;
            particleSize = 4f;
            rotationSpeed = 1.0f;
        }

        float rotation = animationTimer * rotationSpeed;

        for (int i = 0; i < numParticles; i++) {
            float angle = rotation + (i * (float)Math.PI * 2f / numParticles);
            float radius = baseRadius + (i % 2) * 8f;
            float fx = x + (float)Math.cos(angle) * radius;
            float fy = y + (float)Math.sin(angle) * radius;

            Misc.renderQuad(fx - particleSize/2, fy - particleSize/2, particleSize, particleSize, effectColor, alphaMult * 0.9f);
        }
    }

    private void drawStars(GachaItem item, float x, float y, float alphaMult) {
        Color starColor = item.rarity >= 4 ? new Color(255, 215, 0) : Color.YELLOW;
        float starSpacing = 12f;
        float totalWidth = (item.rarity - 1) * starSpacing;
        float startX = x - totalWidth / 2f;

        for (int i = 0; i < item.rarity; i++) {
            float starX = startX + i * starSpacing;
            drawRatingStar(starX, y, starColor, alphaMult);
        }
    }

    private void drawRatingStar(float x, float y, Color color, float alphaMult) {
        float size = 5f;
        float halfSize = size / 2f;
        float quarterSize = size / 4f;

        Misc.renderQuad(x - quarterSize, y - halfSize, quarterSize * 2, size, color, alphaMult);
        Misc.renderQuad(x - halfSize, y - quarterSize, size, quarterSize * 2, color, alphaMult);
    }

    private void drawStarShape(float x, float y, float width, float height, Color color, float alphaMult) {
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



    public void advance(float amount) {
        if (p == null) return;

        animationTimer += amount;

        // Update revealed items
        for (GachaItem item : revealedItems) {
            if (!item.isFixed) {
                // Item is still spinning
                item.spinTimer += amount;

                // Check if spinning phase is complete (3 rotations worth of time)
                if (item.spinTimer >= SPIN_DURATION) {
                    item.isFixed = true;
                    item.spinTimer = SPIN_DURATION; // Cap at max

                    // Trigger particle burst when item becomes fixed
                    triggerParticleBurst(item);
                }
            }
        }

        // Update particle bursts
        activeBursts.removeIf(burst -> !burst.advance(amount));

        // Check if all items revealed and all fixed
        if (currentRevealIndex >= allItems.size() && !animationComplete) {
            boolean allFixed = true;
            for (GachaItem item : revealedItems) {
                if (!item.isFixed) {
                    allFixed = false;
                    break;
                }
            }

            if (allFixed && !waitingForClick) {
                // All done, wait a bit then complete
                animationComplete = true;
                if (callback != null) {
                    callback.onAnimationComplete(allItems);
                }
            }
        }

        spinnerFader.advance(amount);
    }

    private void triggerParticleBurst(GachaItem item) {
        int itemsPerColumn = (allItems.size() + 1) / COLUMNS;
        float startX = centerX - COLUMN_SPACING / 2f - ITEM_WIDTH / 2f;
        float startY = centerY + (itemsPerColumn - 1) * ROW_SPACING / 2f;

        int index = revealedItems.indexOf(item);
        int col = index / itemsPerColumn;
        int row = index % itemsPerColumn;
        float itemX = startX + col * COLUMN_SPACING;
        float itemY = startY - row * ROW_SPACING;

        int numParticles = 8 + item.rarity * 2;
        activeBursts.add(new ParticleBurst(itemX, itemY, item.color, numParticles));
    }

    private void revealNextItem() {
        if (currentRevealIndex >= allItems.size()) return;

        GachaItem item = allItems.get(currentRevealIndex);
        item.revealed = true;
        item.spinTimer = 0f;
        item.isFixed = false;
        revealedItems.add(item);
        currentRevealIndex++;

        waitingForClick = false;

        // Play reveal sound (optional)
        // Global.getSoundPlayer().playUISound("gacha_reveal", 1f, 1f);
    }

    public void processInput(List<InputEventAPI> events) {
        if (p == null) return;

        // Check if close button was clicked
        if (closeButton != null && closeButton.isChecked()) {
            closeButton.setChecked(false);
            closeAnimation();
            return;
        }

        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            // Handle click for reveal/close
            if (event.isLMBDownEvent()) {
                // Check if current item is still spinning - if so, skip to fixed state
                if (!waitingForClick && currentRevealIndex > 0 && currentRevealIndex <= allItems.size()) {
                    GachaItem currentItem = revealedItems.get(revealedItems.size() - 1);
                    if (!currentItem.isFixed) {
                        event.consume();
                        // Skip spinning and go directly to fixed state
                        currentItem.isFixed = true;
                        currentItem.spinTimer = SPIN_DURATION;
                        triggerParticleBurst(currentItem);
                        waitingForClick = true;
                        return;
                    }
                }

                // Click to reveal next item
                if (waitingForClick && currentRevealIndex < allItems.size()) {
                    event.consume();
                    revealNextItem();
                    return;
                }

                // Click to close when complete
                if (animationComplete) {
                    event.consume();
                    closeAnimation();
                    return;
                }
            }

            // Handle keyboard input
            if (event.isKeyDownEvent()) {
                int key = event.getEventValue();

                if (key == Keyboard.KEY_ESCAPE) {
                    event.consume();
                    closeAnimation();
                    return;
                }

                if (key == Keyboard.KEY_SPACE || key == Keyboard.KEY_RETURN) {
                    event.consume();

                    // Check if current item is still spinning - if so, skip to fixed state
                    if (!waitingForClick && currentRevealIndex > 0 && currentRevealIndex <= allItems.size()) {
                        GachaItem currentItem = revealedItems.get(revealedItems.size() - 1);
                        if (!currentItem.isFixed) {
                            // Skip spinning and go directly to fixed state
                            currentItem.isFixed = true;
                            currentItem.spinTimer = SPIN_DURATION;
                            triggerParticleBurst(currentItem);
                            waitingForClick = true;
                            return;
                        }
                    }

                    if (waitingForClick && currentRevealIndex < allItems.size()) {
                        revealNextItem();
                    } else if (animationComplete) {
                        closeAnimation();
                    }
                    return;
                }
            }
        }
    }

    private void closeAnimation() {
        animationComplete = true;
        if (callback != null) {
            callback.onAnimationComplete(allItems);
        }
        if (callbacks != null) {
            callbacks.dismissDialog();
        }
    }

    public void startAnimation() {
        animationRunning = true;
        animationComplete = false;
        animationTimer = 0f;
        currentRevealIndex = 0;
        revealedItems.clear();
        activeBursts.clear();
        waitingForClick = false;

        // Reveal first item immediately
        revealNextItem();
    }

    public boolean isAnimationComplete() {
        return animationComplete;
    }

}
