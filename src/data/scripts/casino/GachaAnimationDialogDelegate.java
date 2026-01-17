package data.scripts.casino;

import java.util.Map;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.CustomPanelAPI;

import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;

public class GachaAnimationDialogDelegate implements CustomVisualDialogDelegate {
    
    protected CustomVisualDialogDelegate.DialogCallbacks callbacks;
    protected float endDelay = 1f; // Shorter delay for gacha
    protected boolean finished = false;
    
    protected String musicId;
    protected GachaAnimation gachaAnimation;
    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;
    protected boolean tutorialMode = false;
    
    public GachaAnimationDialogDelegate(String musicId, GachaAnimation gachaAnimation, 
                                      InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        this.musicId = musicId;
        this.gachaAnimation = gachaAnimation;
        this.dialog = dialog;
        this.memoryMap = memoryMap;
    }
    
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return gachaAnimation;
    }
    
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.callbacks = callbacks;
        
        // Set fade duration for exit
        callbacks.getPanelFader().setDurationOut(1f);
        
        // Initialize the gacha animation
        gachaAnimation.init(panel, callbacks, dialog);
        
        // Start the animation immediately
        gachaAnimation.startAnimation();
    }
    
    public float getNoiseAlpha() {
        // No noise for gacha animation
        return 0;
    }
    
    public void advance(float amount) {
        // Handle animation completion
        if (!finished && gachaAnimation.isAnimationComplete()) {
            // Reduce the delay to 0 immediately when animation completes
            // This allows for instant dismissal when animation is skipped
            endDelay = 0f;
            
            // Fade out the panel
            callbacks.getPanelFader().fadeOut();
            if (callbacks.getPanelFader().isFadedOut()) {
                // Don't dismiss the dialog immediately, let the callback handle it
                // callbacks.dismissDialog();
                finished = true;
            }
        }
    }
    
    public void reportDismissed(int option) {
        // Clean up any audio or other resources
        // In the future, we might play completion sounds here
        
        if (memoryMap != null) { // null when called from test dialogs
            // Fire event when gacha animation completes
            FireBest.fire(null, dialog, memoryMap, "GachaAnimationCompleted");
        }
    }
}