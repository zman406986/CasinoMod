package data.scripts.casino.gacha;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;

import data.scripts.casino.gacha.ShipPickerPanelUI.ShipPickerCallback;
//need to reinvent the ShipPicker because base game api's version doesn't allow modification
public class ShipPickerDialogDelegate implements CustomVisualDialogDelegate {

    protected DialogCallbacks callbacks;

    protected final ShipPickerPanelUI pickerPanel;
    protected final InteractionDialogAPI dialog;
    protected final Map<String, MemoryAPI> memoryMap;
    protected final ShipPickerCallback callback;

    public ShipPickerDialogDelegate(
            List<FleetMemberAPI> ships,
            Set<String> autoConvertHullIds,
            InteractionDialogAPI dialog,
            Map<String, MemoryAPI> memoryMap,
            ShipPickerCallback callback) {

        this.dialog = dialog;
        this.memoryMap = memoryMap;
        this.callback = callback;

        pickerPanel = new ShipPickerPanelUI(ships, autoConvertHullIds, callback);
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return pickerPanel;
    }

    @Override
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.callbacks = callbacks;

        callbacks.getPanelFader().setDurationOut(0.3f);

        pickerPanel.init(panel, callbacks);
    }

    @Override
    public float getNoiseAlpha() {
        return 0.3f;
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void reportDismissed(int option) {
    }
}