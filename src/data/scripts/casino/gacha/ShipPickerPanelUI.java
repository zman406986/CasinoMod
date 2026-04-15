package data.scripts.casino.gacha;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.ActionListenerDelegate;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;

import data.scripts.casino.CasinoConfig;
import data.scripts.casino.Strings;
//need to reinvent the ShipPicker because base game api's version doesn't allow modification
public class ShipPickerPanelUI extends BaseCustomUIPanelPlugin implements ActionListenerDelegate {

    private static final SettingsAPI settings = Global.getSettings();

    private static final float PANEL_WIDTH = 1000f;
    private static final float PANEL_HEIGHT = 700f;

    private static final float SHIP_BOX_WIDTH = 120f;
    private static final float SHIP_BOX_HEIGHT = 90f;
    private static final float SHIP_SPRITE_SIZE = 60f;
    private static final float NAME_HEIGHT = 16f;
    private static final float VALUE_HEIGHT = 14f;
    private static final float CHECKBOX_SIZE = 20f;

    private static final int COLS = 5;
    private static final int ROWS = 2;
    private static final float GAP_X = 15f;
    private static final float GAP_Y = 20f;

    private static final float MARGIN = 30f;
    private static final float HEADER_HEIGHT = 40f;
    private static final float BUTTON_HEIGHT = 40f;
    private static final float BUTTON_AREA_HEIGHT = 60f;

    private static final Color COLOR_BG_DARK = new Color(15, 15, 20);
    private static final Color COLOR_BOX_BG = new Color(40, 40, 50);
    private static final Color COLOR_AUTO_CONVERT = new Color(255, 215, 0);
    private static final Color COLOR_MANUAL_SELECTED = new Color(50, 200, 50);
    private static final Color COLOR_CHECKBOX_GOLD = new Color(255, 215, 0);
    private static final Color COLOR_CHECKBOX_GREEN = new Color(50, 200, 50);
    private static final Color COLOR_UNSELECTED = new Color(100, 100, 120);
    private static final Color COLOR_AUTO_TAG = new Color(255, 200, 50);
    private static final Color COLOR_MANUAL_TAG = new Color(100, 255, 100);

    private static final String ACTION_CONVERT = "picker_convert";
    private static final String ACTION_KEEP_ALL = "picker_keep_all";
    private static final String ACTION_SELECT_SHIP = "picker_ship_";

    private final List<FleetMemberAPI> ships;
    private final Set<String> autoConvertHullIds;
    private final ShipPickerCallback callback;

    private CustomPanelAPI panel;
    private DialogCallbacks callbacks;

    private final Set<Integer> selectedIndices = new HashSet<>();
    private final Set<Integer> autoConvertIndices = new HashSet<>();
    private final Map<Integer, Integer> shipValues = new HashMap<>();

    private LabelAPI titleLabel;
    private LabelAPI autoCountLabel;
    private LabelAPI selectedCountLabel;

    private final List<LabelAPI> shipNameLabels = new ArrayList<>();

    private ButtonAPI convertButton;
    private ButtonAPI keepAllButton;
    private final List<ButtonAPI> shipButtons = new ArrayList<>();

    private final Map<String, SpriteAPI> spriteCache = new HashMap<>();

    private boolean buttonsCreated = false;

    public interface ShipPickerCallback {
        void onConvert(List<FleetMemberAPI> selected);
        void onKeepAll();
    }

    public ShipPickerPanelUI(List<FleetMemberAPI> ships, Set<String> autoConvertHullIds, ShipPickerCallback callback) {
        this.ships = ships;
        this.autoConvertHullIds = autoConvertHullIds != null ? autoConvertHullIds : new HashSet<>();
        this.callback = callback;

        initializeSelectionState();
        cacheShipSprites();
    }

    private void initializeSelectionState() {
        autoConvertIndices.clear();
        selectedIndices.clear();
        shipValues.clear();

        for (int i = 0; i < ships.size(); i++) {
            FleetMemberAPI ship = ships.get(i);
            if (ship != null && ship.getHullId() != null) {
                int value = calculateShipValue(ship);
                shipValues.put(i, value);

                if (autoConvertHullIds.contains(ship.getHullId())) {
                    autoConvertIndices.add(i);
                    selectedIndices.add(i);
                }
            }
        }
    }

    private int calculateShipValue(FleetMemberAPI ship) {
        if (ship == null) return 0;
        return (int)(ship.getHullSpec().getBaseValue() / CasinoConfig.SHIP_TRADE_RATE * CasinoConfig.SHIP_SELL_MULTIPLIER);
    }

    private void cacheShipSprites() {
        for (FleetMemberAPI ship : ships) {
            if (ship != null && ship.getHullId() != null) {
                getShipSprite(ship.getHullId());
            }
        }
    }

    private SpriteAPI getShipSprite(String hullId) {
        if (hullId == null || hullId.isEmpty()) return null;

        if (spriteCache.containsKey(hullId)) {
            return spriteCache.get(hullId);
        }

        try {
            ShipHullSpecAPI spec = settings.getHullSpec(hullId);
            if (spec == null) {
                spriteCache.put(hullId, null);
                return null;
            }

            String spriteName = spec.getSpriteName();
            if (spriteName == null || spriteName.isEmpty()) {
                spriteCache.put(hullId, null);
                return null;
            }

            SpriteAPI sprite = settings.getSprite(spriteName);
            spriteCache.put(hullId, sprite);
            return sprite;
        } catch (Exception e) {
            spriteCache.put(hullId, null);
            return null;
        }
    }

    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.panel = panel;
        this.callbacks = callbacks;

        callbacks.getPanelFader().setDurationOut(0.3f);

        createUIElements();
        updateLabels();
    }

    private void createUIElements() {
        if (panel == null) return;

        createHeaderLabels();
        createShipLabels();
        createButtons();
    }

    private void createHeaderLabels() {
        titleLabel = settings.createLabel(Strings.get("gacha_picker.title"), Fonts.DEFAULT_SMALL);
        titleLabel.setColor(Color.CYAN);
        titleLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) titleLabel).inTL(MARGIN, MARGIN)
            .setSize(PANEL_WIDTH - MARGIN * 2, HEADER_HEIGHT);

        autoCountLabel = settings.createLabel(Strings.format("gacha_picker.auto_count", autoConvertIndices.size()), Fonts.DEFAULT_SMALL);
        autoCountLabel.setColor(COLOR_AUTO_CONVERT);
        autoCountLabel.setAlignment(Alignment.RMID);
        panel.addComponent((UIComponentAPI) autoCountLabel).inTL(MARGIN, MARGIN)
            .setSize(PANEL_WIDTH - MARGIN * 2, HEADER_HEIGHT);

        selectedCountLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        selectedCountLabel.setColor(Color.WHITE);
        selectedCountLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) selectedCountLabel).inTL(MARGIN, MARGIN + HEADER_HEIGHT)
            .setSize(PANEL_WIDTH - MARGIN * 2, 20f);
    }

    private void createShipLabels() {
        float startY = MARGIN + HEADER_HEIGHT + 20f;

        for (int i = 0; i < ships.size() && i < COLS * ROWS; i++) {
            FleetMemberAPI ship = ships.get(i);
            if (ship == null) continue;

            int col = i % COLS;
            int row = i / COLS;
            float boxX = MARGIN + col * (SHIP_BOX_WIDTH + GAP_X);
            float boxY = startY + row * (SHIP_BOX_HEIGHT + NAME_HEIGHT + VALUE_HEIGHT + GAP_Y + CHECKBOX_SIZE);

            String name = ship.getShipName() != null ? ship.getShipName() : 
                          (ship.getHullSpec() != null ? ship.getHullSpec().getHullName() : "Unknown");

            LabelAPI nameLbl = settings.createLabel(name, Fonts.DEFAULT_SMALL);
            nameLbl.setColor(Color.WHITE);
            nameLbl.setAlignment(Alignment.MID);
            nameLbl.getPosition().setSize(SHIP_BOX_WIDTH, NAME_HEIGHT);
            panel.addComponent((UIComponentAPI) nameLbl).inTL(boxX, boxY + SHIP_BOX_HEIGHT + CHECKBOX_SIZE);
            shipNameLabels.add(nameLbl);

            int value = shipValues.getOrDefault(i, 0);
            LabelAPI valueLbl = settings.createLabel(Strings.format("gacha_picker.value_format", value), Fonts.DEFAULT_SMALL);
            valueLbl.setColor(Color.GRAY);
            valueLbl.setAlignment(Alignment.MID);
            valueLbl.getPosition().setSize(SHIP_BOX_WIDTH, VALUE_HEIGHT);
            panel.addComponent((UIComponentAPI) valueLbl).inTL(boxX, boxY + SHIP_BOX_HEIGHT + CHECKBOX_SIZE + NAME_HEIGHT);
        }
    }

    private void createButtons() {
        if (panel == null || buttonsCreated) return;

        TooltipMakerAPI btnTp = panel.createUIElement(PANEL_WIDTH, PANEL_HEIGHT, false);
        btnTp.setActionListenerDelegate(this);
        panel.addUIElement(btnTp).inTL(0, 0);

        float startY = MARGIN + HEADER_HEIGHT + 20f;

        for (int i = 0; i < ships.size() && i < COLS * ROWS; i++) {
            int col = i % COLS;
            int row = i / COLS;
            float boxX = MARGIN + col * (SHIP_BOX_WIDTH + GAP_X);
            float boxY = startY + row * (SHIP_BOX_HEIGHT + NAME_HEIGHT + VALUE_HEIGHT + GAP_Y + CHECKBOX_SIZE);

            ButtonAPI shipBtn = btnTp.addButton("", ACTION_SELECT_SHIP + i, SHIP_BOX_WIDTH, SHIP_BOX_HEIGHT + CHECKBOX_SIZE, 0f);
            shipBtn.getPosition().inTL(boxX, boxY);
            shipBtn.setOpacity(0f);
            shipBtn.setQuickMode(true);
            shipButtons.add(shipBtn);
        }

        float buttonAreaY = PANEL_HEIGHT - BUTTON_AREA_HEIGHT;
        float centerX = PANEL_WIDTH / 2f;

        convertButton = btnTp.addButton(Strings.format("gacha_picker.convert_btn", getSelectedCount()), ACTION_CONVERT, 180f, BUTTON_HEIGHT, 0f);
        convertButton.getPosition().inTL(centerX - 180f - 50f, buttonAreaY);
        convertButton.setQuickMode(true);

        keepAllButton = btnTp.addButton(Strings.get("gacha_picker.keep_all_btn"), ACTION_KEEP_ALL, 160f, BUTTON_HEIGHT, 0f);
        keepAllButton.getPosition().inTL(centerX + 50f, buttonAreaY);
        keepAllButton.setQuickMode(true);
        keepAllButton.setShortcut(Keyboard.KEY_ESCAPE, false);

        buttonsCreated = true;
    }

    private int getSelectedCount() {
        return selectedIndices.size();
    }

    private void updateLabels() {
        autoCountLabel.setText(Strings.format("gacha_picker.auto_count", autoConvertIndices.size()));

        int selectedCount = getSelectedCount();
        selectedCountLabel.setText(Strings.format("gacha_picker.selected_count", selectedCount));

        if (convertButton != null) {
            convertButton.setText(Strings.format("gacha_picker.convert_btn", selectedCount));
        }

        for (int i = 0; i < shipNameLabels.size() && i < ships.size(); i++) {
            boolean isSelected = selectedIndices.contains(i);
            boolean isAuto = autoConvertIndices.contains(i);

            if (isSelected) {
                if (isAuto) {
                    shipNameLabels.get(i).setColor(COLOR_AUTO_TAG);
                } else {
                    shipNameLabels.get(i).setColor(COLOR_MANUAL_TAG);
                }
            } else {
                shipNameLabels.get(i).setColor(Color.WHITE);
            }
        }
    }

    public void renderBelow(float alphaMult) {
        PositionAPI pos = panel.getPosition();
        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = pos.getHeight();

        Misc.renderQuad(x, y, w, h, COLOR_BG_DARK, alphaMult);

        renderShipBoxes(x, y, alphaMult);
        updateLabels();
        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        for (ButtonAPI btn : shipButtons) {
            if (btn != null) {
                btn.setOpacity(0.01f);
            }
        }
        if (convertButton != null) {
            convertButton.setOpacity(1f);
        }
        if (keepAllButton != null) {
            keepAllButton.setOpacity(1f);
        }
    }

    private void renderShipBoxes(float panelX, float panelY, float alphaMult) {
        float startY = MARGIN + HEADER_HEIGHT + 20f;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        for (int i = 0; i < ships.size() && i < COLS * ROWS; i++) {
            FleetMemberAPI ship = ships.get(i);
            if (ship == null) continue;

            int col = i % COLS;
            int row = i / COLS;
            float boxX = MARGIN + col * (SHIP_BOX_WIDTH + GAP_X);
            float boxY = startY + row * (SHIP_BOX_HEIGHT + NAME_HEIGHT + VALUE_HEIGHT + GAP_Y + CHECKBOX_SIZE);

            float screenY = panelY + PANEL_HEIGHT - boxY - SHIP_BOX_HEIGHT;

            boolean isSelected = selectedIndices.contains(i);
            boolean isAuto = autoConvertIndices.contains(i);

            float boxAlpha = isSelected ? 1f : 0.7f;

            Misc.renderQuad(panelX + boxX, screenY, SHIP_BOX_WIDTH, SHIP_BOX_HEIGHT, COLOR_BOX_BG, alphaMult * 0.9f * boxAlpha);

            Color borderColor;
            if (isSelected) {
                borderColor = isAuto ? COLOR_AUTO_CONVERT : COLOR_MANUAL_SELECTED;
            } else {
                borderColor = COLOR_UNSELECTED;
            }

            GL11.glColor4f(borderColor.getRed() / 255f, borderColor.getGreen() / 255f, borderColor.getBlue() / 255f, alphaMult * 0.9f);
            GL11.glLineWidth(2f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(panelX + boxX, screenY);
            GL11.glVertex2f(panelX + boxX + SHIP_BOX_WIDTH, screenY);
            GL11.glVertex2f(panelX + boxX + SHIP_BOX_WIDTH, screenY + SHIP_BOX_HEIGHT);
            GL11.glVertex2f(panelX + boxX, screenY + SHIP_BOX_HEIGHT);
            GL11.glEnd();

            renderCheckbox(panelX + boxX, screenY - CHECKBOX_SIZE + 3f, isSelected, isAuto, alphaMult);

            SpriteAPI sprite = getShipSprite(ship.getHullId());
            if (sprite != null) {
                renderShipSprite(sprite, panelX + boxX, screenY, alphaMult * boxAlpha);
            }
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(1f);
    }

    private void renderCheckbox(float x, float y, boolean isSelected, boolean isAuto, float alphaMult) {
        float size = CHECKBOX_SIZE;
        float margin = 3f;

        GL11.glDisable(GL11.GL_TEXTURE_2D);

        Misc.renderQuad(x, y, size, size, new Color(30, 30, 40), alphaMult * 0.8f);

        Color borderColor = isSelected ? (isAuto ? COLOR_CHECKBOX_GOLD : COLOR_CHECKBOX_GREEN) : COLOR_UNSELECTED;
        GL11.glColor4f(borderColor.getRed() / 255f, borderColor.getGreen() / 255f, borderColor.getBlue() / 255f, alphaMult);
        GL11.glLineWidth(1.5f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x + margin, y + margin);
        GL11.glVertex2f(x + size - margin, y + margin);
        GL11.glVertex2f(x + size - margin, y + size - margin);
        GL11.glVertex2f(x + margin, y + size - margin);
        GL11.glEnd();

        if (isSelected) {
            GL11.glLineWidth(2f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(x + margin + 2f, y + margin + 4f);
            GL11.glVertex2f(x + size / 2f - 1f, y + size / 2f);
            GL11.glVertex2f(x + size / 2f - 1f, y + size / 2f);
            GL11.glVertex2f(x + size - margin - 2f, y + size - margin - 2f);
            GL11.glEnd();
        }

        GL11.glLineWidth(1f);
    }

    private void renderShipSprite(SpriteAPI sprite, float boxX, float boxY, float alphaMult) {
        float spriteWidth = sprite.getWidth();
        float spriteHeight = sprite.getHeight();
        float maxDim = Math.max(spriteWidth, spriteHeight);
        float scale = SHIP_SPRITE_SIZE / maxDim;

        float scaledWidth = spriteWidth * scale;
        float scaledHeight = spriteHeight * scale;

        float centerX = boxX + SHIP_BOX_WIDTH / 2f;
        float centerY = boxY + SHIP_BOX_HEIGHT / 2f;

        sprite.setSize(scaledWidth, scaledHeight);
        sprite.setColor(Color.WHITE);
        sprite.setAlphaMult(alphaMult);
        sprite.setNormalBlend();
        sprite.renderAtCenter(centerX, centerY);
    }

    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            if (event.isKeyDownEvent()) {
                int key = event.getEventValue();
                if (key == Keyboard.KEY_ESCAPE) {
                    callbacks.dismissDialog();
                    event.consume();
                    return;
                }
            }
        }
    }

    @Override
    public void actionPerformed(Object input, Object source) {
        if (!(source instanceof ButtonAPI btn)) return;
        
        Object data = btn.getCustomData();
        if (data == null) return;

        String action = data.toString();

        if (action.equals(ACTION_CONVERT)) {
            List<FleetMemberAPI> selectedShips = new ArrayList<>();
            for (int idx : selectedIndices) {
                if (idx >= 0 && idx < ships.size()) {
                    selectedShips.add(ships.get(idx));
                }
            }
            if (callback != null) {
                callback.onConvert(selectedShips);
            }
            callbacks.dismissDialog();
        } else if (action.equals(ACTION_KEEP_ALL)) {
            if (callback != null) {
                callback.onKeepAll();
            }
            callbacks.dismissDialog();
        } else if (action.startsWith(ACTION_SELECT_SHIP)) {
            int idx = Integer.parseInt(action.substring(ACTION_SELECT_SHIP.length()));
            if (idx >= 0 && idx < ships.size()) {
                if (selectedIndices.contains(idx)) {
                    selectedIndices.remove(idx);
                } else {
                    selectedIndices.add(idx);
                }
                updateLabels();
            }
        }
    }
}