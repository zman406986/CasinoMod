package data.scripts.casino.interaction;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;

public class CosmiconLoungeHandler {

    private final CasinoInteraction casino;
    private final LoungeProvider provider;

    public CosmiconLoungeHandler(CasinoInteraction casino, LoungeProvider provider) {
        this.casino = casino;
        this.provider = provider;
    }

    public LoungeProvider getProvider() {
        return provider;
    }

    public void showLounge() {
        InteractionDialogAPI dialog = casino.getDialog();
        dialog.setPlugin(casino);

        dialog.getOptionPanel().clearOptions();

        provider.showLounge(dialog);

        for (LoungeProvider.MenuOption opt : provider.getMenuOptions(dialog)) {
            dialog.getOptionPanel().addOption(opt.label(), opt.optionId(), opt.tooltip());
            if (!opt.enabled()) {
                dialog.getOptionPanel().setEnabled(opt.optionId(), false);
            }
        }
    }

    public void handle(String option) {
        InteractionDialogAPI dialog = casino.getDialog();
        Runnable backToLounge = this::showLounge;
        Runnable backToCasino = casino::showMenu;

        provider.handleOption(dialog, option, backToLounge, backToCasino);
    }
}
