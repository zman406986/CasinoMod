package data.console.commands;

import data.scripts.casino.CasinoConfig;
import data.scripts.casino.gacha.CasinoGachaManager;
import data.scripts.casino.gacha.CasinoGachaManager.GachaData;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class CasinoPity implements BaseCommand {

    @Override
    public CommandResult runCommand(@NotNull String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        CasinoGachaManager manager = new CasinoGachaManager();
        GachaData data = manager.getData();

        String trimmed = args.trim().toLowerCase();

        if (trimmed.isEmpty() || trimmed.equals("near_max")) {
            int nearMax5 = CasinoConfig.PITY_HARD_5 - 2;
            int nearMax4 = CasinoConfig.PITY_HARD_4 - 1;
            data.pity5 = nearMax5;
            data.pity4 = nearMax4;
            Console.showMessage("Pity set to near-max: 5★=" + nearMax5 + ", 4★=" + nearMax4);
            Console.showMessage("Next pull: soft pity for 5★, guaranteed for 4★");
            return CommandResult.SUCCESS;
        }

        String[] parts = trimmed.split("\\s+");
        try {
            int pity5 = Integer.parseInt(parts[0]);
            data.pity5 = pity5;
            Console.showMessage("5★ pity set to: " + pity5);

            if (parts.length >= 2) {
                int pity4 = Integer.parseInt(parts[1]);
                data.pity4 = pity4;
                Console.showMessage("4★ pity set to: " + pity4);
            }
        } catch (NumberFormatException e) {
            Console.showMessage("Error: pity values must be integers.");
            return CommandResult.BAD_SYNTAX;
        }

        return CommandResult.SUCCESS;
    }
}
