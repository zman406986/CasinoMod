package data.scripts.casino;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
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

    public static interface GachaAnimationCallback {
        void onAnimationComplete(List<GachaItem> results);
    }

    public static class GachaItem {
        public String id;
        public String name;
        public String hullId;
        public String weaponId;
        public String hullModId;
        public int rarity;
        public String spritePath;
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
            switch(rarity) {
                case 1: color = new Color(150, 150, 150); break;
                case 2: color = new Color(100, 200, 100); break;
                case 3: color = new Color(100, 150, 255); break;
                case 4: color = new Color(200, 100, 255); break;
                case 5: color = new Color(255, 215, 0); break;
                default: color = Color.WHITE; break;
            }
        }

        public void setHullId(String hullId) {
            this.hullId = hullId;
        }

        public void setWeaponId(String weaponId) {
            this.weaponId = weaponId;
        }

        public void setHullModId(String hullModId) {
            this.hullModId = hullModId;
        }

        public void setSpritePath(String spritePath) {
            this.spritePath = spritePath;
        }

        // Get spin speed based on rarity
        public float getSpinSpeed() {
            switch(rarity) {
                case 5: return 12f;
                case 4: return 9f;
                case 3: return 7f;
                default: return 6f;
            }
        }

        // Get dark color for spinning phase
        public Color getDarkColor() {
            return new Color(
                (int)(color.getRed() * 0.4f),
                (int)(color.getGreen() * 0.4f),
                (int)(color.getBlue() * 0.4f)
            );
        }

        // Get background alpha based on rarity
        public float getBackgroundAlpha() {
            switch(rarity) {
                case 5: return 0.6f;
                case 4: return 0.5f;
                case 3: return 0.4f;
                case 2: return 0.35f;
                default: return 0.3f;
            }
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
            float size = 4f * (1f - progress);

            for (Pair<Float, Float> particle : particles) {
                float angle = particle.one;
                float speed = particle.two;
                float dist = speed * lifetime;
                float px = x + (float)Math.cos(angle) * dist;
                float py = y + (float)Math.sin(angle) * dist;

                Misc.renderQuad(px - size/2, py - size/2, size, size, color, alpha);
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

    public void render(float alphaMult) {
        if (p == null) return;

        float x = p.getX();
        float y = p.getY();
        float w = p.getWidth();
        float h = p.getHeight();

        // Enable scissor test to clip rendering to panel boundaries
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        float s = Global.getSettings().getScreenScaleMult();
        GL11.glScissor((int)(x * s), (int)(y * s), (int)(w * s), (int)(h * s));

        // Draw background effect
        if (backgroundSprite != null) {
            backgroundSprite.setSize(w, h);
            backgroundSprite.setAlphaMult(alphaMult * 0.3f);
            backgroundSprite.render(x, y);
        }

        // Draw title
        drawTitle(alphaMult);

        // Draw revealed items in multi-column layout
        drawRevealedItems(alphaMult);

        // Draw "click to continue" hint if waiting
        if (waitingForClick && currentRevealIndex < allItems.size()) {
            drawContinueHint(alphaMult);
        }

        // Disable scissor test after rendering
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void drawTitle(float alphaMult) {
        // Title is drawn via the dialog's text panel, not here
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

        // Determine colors based on phase
        Color displayColor;
        Color borderColor;
        float bgAlpha;

        if (!item.isFixed) {
            // Spinning phase - use dark color
            displayColor = item.getDarkColor();
            borderColor = new Color(100, 100, 100);
            bgAlpha = 0.2f;
        } else {
            // Fixed/bright phase - use full color
            displayColor = item.color;
            borderColor = item.rarity >= 4 ? new Color(255, 215, 0) : Color.WHITE;
            bgAlpha = item.getBackgroundAlpha();
        }

        if (!item.isFixed) {
            // Save matrix and apply rotation for spinning items
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0);
            GL11.glRotatef((float)Math.toDegrees(rotation), 0, 0, 1);
            GL11.glTranslatef(-x, -y, 0);
        }

        // Draw item background
        Misc.renderQuad(x - w/2, y - h/2, w, h, displayColor, alphaMult * bgAlpha);

        // Draw border
        Misc.renderQuad(x - w/2, y - h/2, w, 2, borderColor, alphaMult);
        Misc.renderQuad(x - w/2, y + h/2 - 2, w, 2, borderColor, alphaMult);
        Misc.renderQuad(x - w/2, y - h/2, 2, h, borderColor, alphaMult);
        Misc.renderQuad(x + w/2 - 2, y - h/2, 2, h, borderColor, alphaMult);

        if (!item.isFixed) {
            // Restore matrix
            GL11.glPopMatrix();
        }

        // Draw decorative particles for 4* and 5* items only when fixed
        if (item.isFixed && item.rarity >= 4) {
            drawItemRarityEffect(item, x, y, alphaMult);
        }

        // Draw stars only when item is fixed (bright phase)
        if (item.isFixed) {
            drawStars(item, x, y + h/2 + 15, alphaMult);
        }
    }

    private void drawItemRarityEffect(GachaItem item, float x, float y, float alphaMult) {
        // Each 4* and 5* item gets its own spinning particle effect
        Color effectColor;
        int numParticles;
        float baseRadius;
        float particleSize;

        // Use rotation based on animation timer for spinning effect
        float rotation = animationTimer * 1.5f;

        if (item.rarity >= 5) {
            effectColor = new Color(255, 215, 0, (int)(180 * alphaMult)); // Gold
            numParticles = 8;
            baseRadius = 50f;
            particleSize = 6f;
        } else {
            effectColor = new Color(200, 50, 255, (int)(180 * alphaMult)); // Bright purple
            numParticles = 6;
            baseRadius = 40f;
            particleSize = 5f;
        }

        for (int i = 0; i < numParticles; i++) {
            float angle = rotation + (i * (float)Math.PI * 2f / numParticles);
            float radius = baseRadius + (i % 2) * 10f;
            float fx = x + (float)Math.cos(angle) * radius;
            float fy = y + (float)Math.sin(angle) * radius;

            Misc.renderQuad(fx - particleSize/2, fy - particleSize/2, particleSize, particleSize, effectColor, alphaMult * 0.9f);
        }
    }

    private void drawStars(GachaItem item, float x, float y, float alphaMult) {
        Color starColor = item.rarity >= 4 ? new Color(255, 215, 0) : Color.YELLOW;
        float starSpacing = 10f;
        float totalWidth = (item.rarity - 1) * starSpacing;
        float startX = x - totalWidth / 2f;

        for (int i = 0; i < item.rarity; i++) {
            float starX = startX + i * starSpacing;
            Misc.renderQuad(starX - 3, y - 1, 6, 2, starColor, alphaMult);
            Misc.renderQuad(starX - 1, y - 3, 2, 6, starColor, alphaMult);
        }
    }

    private void drawContinueHint(float alphaMult) {
        String hint = "Click or press SPACE to reveal next";
        float hintY = p.getY() + 40f;

        // Draw hint background
        float hintWidth = 300f;
        float hintHeight = 30f;
        float hintX = centerX - hintWidth / 2f;

        Misc.renderQuad(hintX, hintY - hintHeight/2, hintWidth, hintHeight, new Color(0, 0, 0, 150), alphaMult * 0.7f);

        // Pulsing effect
        float pulse = 0.7f + 0.3f * (float)Math.sin(animationTimer * 4f);
        Misc.renderQuad(hintX, hintY - 1, hintWidth, 2, new Color(255, 255, 255, (int)(200 * pulse)), alphaMult);
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
        Iterator<ParticleBurst> burstIter = activeBursts.iterator();
        while (burstIter.hasNext()) {
            ParticleBurst burst = burstIter.next();
            if (!burst.advance(amount)) {
                burstIter.remove();
            }
        }

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

    public boolean isAnimationRunning() {
        return animationRunning;
    }
}
