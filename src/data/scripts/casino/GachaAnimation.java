package data.scripts.casino;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.Misc;

public class GachaAnimation extends BaseCustomUIPanelPlugin {
    
    protected InteractionDialogAPI dialog;
    protected DialogCallbacks callbacks;
    protected CustomPanelAPI panel;
    protected PositionAPI p;
    
    // Animation state
    protected List<GachaItem> spinningItems = new ArrayList<>();
    protected List<GachaItem> resultItems = new ArrayList<>();
    protected boolean animationRunning = false;
    protected boolean animationComplete = false;
    protected float animationTimer = 0f;
    protected float totalAnimationTime = 3f; // 3 seconds for full animation
    
    // Visual elements
    protected FaderUtil spinnerFader = new FaderUtil(0f, 1f, 0.5f, true, true);
    protected SpriteAPI backgroundSprite;
    protected float centerX, centerY;
    
    // Callback for when animation completes
    protected GachaAnimationCallback callback;
    
    public static interface GachaAnimationCallback {
        void onAnimationComplete(List<GachaItem> results);
    }
    
    public static class GachaItem {
        public String id;
        public String name;
        public String hullId; // For ships
        public String weaponId; // For weapons
        public String hullModId; // For hull mods
        public int rarity; // 1-5 stars
        public String spritePath; // Icon path
        public Color color; // Rarity color
        
        public GachaItem(String id, String name, int rarity) {
            this.id = id;
            this.name = name;
            this.rarity = rarity;
            setColorForRarity();
        }
        
        private void setColorForRarity() {
            switch(rarity) {
                case 1: color = new Color(150, 150, 150); break; // Gray
                case 2: color = new Color(100, 200, 100); break; // Green
                case 3: color = new Color(100, 150, 255); break; // Blue
                case 4: color = new Color(200, 100, 255); break; // Purple
                case 5: color = new Color(255, 215, 0); break;   // Gold
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
    }
    
    public GachaAnimation(List<GachaItem> itemsToAnimate, GachaAnimationCallback callback) {
        this.spinningItems.addAll(itemsToAnimate);
        this.callback = callback;
        
        // Initialize the spinner fader
        spinnerFader.fadeIn();
    }
    
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks, InteractionDialogAPI dialog) {
        this.panel = panel;
        this.callbacks = callbacks;
        this.dialog = dialog;
        
        // Load background if needed
        try {
            backgroundSprite = Global.getSettings().getSprite("graphics/campaign/map/screenFlash2.png");
        } catch (Exception e) {
            // Background not found, that's okay
        }
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
        
        // Draw background effect
        if (backgroundSprite != null) {
            backgroundSprite.setSize(w, h);
            backgroundSprite.setAlphaMult(alphaMult * 0.3f);
            backgroundSprite.render(x, y);
        }
        
        // Draw spinning items
        if (!animationComplete) {
            drawSpinningItems(alphaMult);
        } else {
            drawResultItems(alphaMult);
        }
        
        // Draw animation effects
        drawAnimationEffects(alphaMult);
    }

    private void drawSpinningItems(float alphaMult) {
        if (spinningItems.isEmpty()) return;
        
        float itemSpacing = 80f;
        float startY = centerY - (spinningItems.size() - 1) * itemSpacing / 2f;
        
        for (int i = 0; i < spinningItems.size(); i++) {
            GachaItem item = spinningItems.get(i);
            
            // Create spinning effect with animation timer
            float offset = (animationTimer * 10f) + (i * 0.5f);
            float yPosition = startY + i * itemSpacing + (float)Math.sin(offset) * 10f;
            
            // Draw item with pulsing effect
            drawItem(item, centerX, yPosition, alphaMult * (0.7f + 0.3f * (float)Math.sin(offset)));
        }
    }
    
    private void drawResultItems(float alphaMult) {
        if (resultItems.isEmpty()) return;
        
        float itemSpacing = 100f;
        float startY = centerY - (resultItems.size() - 1) * itemSpacing / 2f;
        
        for (int i = 0; i < resultItems.size(); i++) {
            GachaItem item = resultItems.get(i);
            float yPosition = startY + i * itemSpacing;
            
            // Draw result item with shine effect
            drawItem(item, centerX, yPosition, alphaMult);
            
            // Add star rating display
            drawStars(item, centerX + 80, yPosition, alphaMult);
        }
    }
    
    private void drawItem(GachaItem item, float x, float y, float alphaMult) {
        // Draw item name - removing text renderer as it's not available in this context
        
        // Simple rectangle as placeholder for item icon
        Misc.renderQuad(x - 40, y - 15, 80, 30, item.color, alphaMult * 0.3f);
        
        // Draw rarity border - removing renderOutline as it doesn't exist in Misc
    }
    
    private void drawStars(GachaItem item, float x, float y, float alphaMult) {
        Color starColor = item.rarity >= 4 ? new Color(255, 215, 0) : Color.YELLOW;
        
        for (int i = 0; i < item.rarity; i++) {
            float starX = x + i * 12;
            // Draw a simple star representation
            Misc.renderQuad(starX, y - 5, 8, 2, starColor, alphaMult);
            Misc.renderQuad(starX + 3, y - 8, 2, 8, starColor, alphaMult);
        }
    }
    
    private void drawAnimationEffects(float alphaMult) {
        // Draw spinning effect around center
        if (!animationComplete) {
            float radius = 120f + (float)Math.sin(animationTimer * 10f) * 10f;
            for (int i = 0; i < 8; i++) {
                float angle = animationTimer * 5f + (i * (float)Math.PI * 2f / 8f);
                float fx = centerX + (float)Math.cos(angle) * radius;
                float fy = centerY + (float)Math.sin(angle) * radius;
                
                Color effectColor = new Color(255, 215, 0, (int)(100 * alphaMult));
                Misc.renderQuad(fx - 3, fy - 3, 6, 6, effectColor, alphaMult * 0.7f);
            }
        }
    }

    public void advance(float amount) {
        if (p == null) return;
        
        // Update animation timer
        if (animationRunning && !animationComplete) {
            animationTimer += amount;
            
            // Check if animation is complete
            if (animationTimer >= totalAnimationTime) {
                animationComplete = true;
                // Move spinning items to results
                resultItems.clear();
                resultItems.addAll(spinningItems);
                
                // Notify callback
                if (callback != null) {
                    callback.onAnimationComplete(resultItems);
                }
                
                // Dismiss the animation dialog after notifying callback
                if (callbacks != null) {
                    callbacks.dismissDialog();
                }
            }
        }
        
        // Update faders
        spinnerFader.advance(amount);
    }

    public void processInput(List<InputEventAPI> events) {
        if (p == null) return;
        
        // Allow skipping animation with spacebar or enter
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            
            if (event.isKeyDownEvent() && 
                (event.getEventValue() == org.lwjgl.input.Keyboard.KEY_SPACE ||
                 event.getEventValue() == org.lwjgl.input.Keyboard.KEY_RETURN ||
                 event.getEventValue() == org.lwjgl.input.Keyboard.KEY_ESCAPE)) {
                event.consume();
                
                // Skip to end if animation is running
                if (animationRunning && !animationComplete) {
                    animationTimer = totalAnimationTime;
                    animationComplete = true;
                    resultItems.clear();
                    resultItems.addAll(spinningItems);
                    
                    if (callback != null) {
                        callback.onAnimationComplete(resultItems);
                    }
                    
                    // Dismiss when complete
                    if (callbacks != null) {
                        callbacks.dismissDialog();
                    }
                } else if (animationComplete) {
                    // Dismiss when complete
                    if (callbacks != null) {
                        callbacks.dismissDialog();
                    }
                }
            }
        }
    }
    
    public void startAnimation() {
        animationRunning = true;
        animationComplete = false;
        animationTimer = 0f;
        resultItems.clear();
    }
    
    public boolean isAnimationComplete() {
        return animationComplete;
    }
    
    public boolean isAnimationRunning() {
        return animationRunning;
    }
}