package data.scripts.casino.gacha;

import java.util.Map;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.CustomPanelAPI;

public class GachaAnimationDialogDelegate implements CustomVisualDialogDelegate {
    
    protected CustomVisualDialogDelegate.DialogCallbacks callbacks;
    
    protected String musicId;
    protected GachaAnimation gachaAnimation;
    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;
    protected Runnable onDismissCallback;
    
    public GachaAnimationDialogDelegate(String musicId, GachaAnimation gachaAnimation, 
                                      InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap,
                                      Runnable onDismissCallback) {
        this.musicId = musicId;
        this.gachaAnimation = gachaAnimation;
        this.dialog = dialog;
        this.memoryMap = memoryMap;
        this.onDismissCallback = onDismissCallback;
    }
    
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return gachaAnimation;
    }
    
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.callbacks = callbacks;
        callbacks.getPanelFader().setDurationOut(1f);
        gachaAnimation.init(panel, callbacks);
        gachaAnimation.startAnimation();
    }
    
    public float getNoiseAlpha() {
        return 0.3f;
    }
    
    public void advance(float amount) {
    }
    
    public void reportDismissed(int option) {
        if (memoryMap != null) {
            FireBest.fire(null, dialog, memoryMap, "GachaAnimationCompleted");
        }
        if (onDismissCallback != null) {
            onDismissCallback.run();
        }
    }
}