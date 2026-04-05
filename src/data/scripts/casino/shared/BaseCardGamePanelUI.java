package data.scripts.casino.shared;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.ActionListenerDelegate;

public abstract class BaseCardGamePanelUI<T> extends BaseCustomUIPanelPlugin
    implements ActionListenerDelegate {

    protected static final SettingsAPI settings = Global.getSettings();
    
    protected CustomPanelAPI panel;
    protected T game;
    protected boolean buttonsCreated = false;

    protected BaseCardGamePanelUI(T game) {
        this.game = game;
    }

    protected abstract void createButtonsInInit();
    protected abstract void updateButtonVisibility();
    protected abstract void processAction(Object data);

    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.panel = panel;
        callbacks.getPanelFader().setDurationOut(0.5f);
        createButtonsInInit();
    }

    @Override
    public void actionPerformed(Object input, Object source) {
        if (source instanceof ButtonAPI btn) {
            processAction(btn.getCustomData());
        }
        updateButtonVisibility();
    }
}
