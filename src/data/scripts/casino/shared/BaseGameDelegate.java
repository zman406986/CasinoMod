package data.scripts.casino.shared;

import java.util.Map;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.CustomPanelAPI;

public abstract class BaseGameDelegate implements CustomVisualDialogDelegate {
    protected DialogCallbacks callbacks;
    protected float endDelay = 1.5f;
    protected boolean finished = false;

    protected final InteractionDialogAPI dialog;
    protected final Map<String, MemoryAPI> memoryMap;
    protected final Runnable onDismissCallback;

    protected BaseGameDelegate(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap,
            Runnable onDismissCallback) {
        this.dialog = dialog;
        this.memoryMap = memoryMap;
        this.onDismissCallback = onDismissCallback;
    }

    @Override
    public abstract CustomUIPanelPlugin getCustomPanelPlugin();

    @Override
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.callbacks = callbacks;
        callbacks.getPanelFader().setDurationOut(0.5f);
    }

    @Override
    public float getNoiseAlpha() {
        return 0.2f;
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void reportDismissed(int option) {
        String eventName = getCompletionEventName();
        if (memoryMap != null && eventName != null) {
            FireBest.fire(null, dialog, memoryMap, eventName);
        }

        onDismissCallback.run();
    }

    protected abstract String getCompletionEventName();

    public void closeDialog() {
        callbacks.dismissDialog();
    }

    public boolean isFinished() {
        return finished;
    }

    protected void resetState() {
        finished = false;
        endDelay = 1.5f;
    }
}
