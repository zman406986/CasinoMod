package data.scripts.casino.interaction;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import java.util.List;

public interface LoungeProvider {

    record MenuOption(String optionId, String label, boolean enabled, String tooltip) {}

    String getString(String key);
    String formatString(String key, Object... args);

    String getEnterButtonLabel();
    List<String> getHelpLines();
    String getHelpOptionLabel();

    void showLounge(InteractionDialogAPI dialog);
    List<MenuOption> getMenuOptions(InteractionDialogAPI dialog);
    void handleOption(InteractionDialogAPI dialog, String option, Runnable onReturnToLounge, Runnable onReturnToCasino);
}
