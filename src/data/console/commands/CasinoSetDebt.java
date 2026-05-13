package data.console.commands;

import data.scripts.casino.CasinoDebtScript;
import data.scripts.casino.CasinoVIPManager;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class CasinoSetDebt implements BaseCommand {

    @Override
    public CommandResult runCommand(@NotNull String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        String trimmed = args.trim().toLowerCase();
        if (!trimmed.startsWith("max")) {
            Console.showMessage("Usage: casino_set_debt max [spawn]");
            return CommandResult.BAD_SYNTAX;
        }

        boolean spawn = trimmed.contains("spawn");

        int maxDebt = CasinoVIPManager.getMaxDebt();
        int currentBalance = CasinoVIPManager.getBalance();
        int delta = -maxDebt - currentBalance;

        CasinoVIPManager.addToBalance(delta);

        Console.showMessage("Debt set to max: " + maxDebt + " stargems");
        Console.showMessage("New balance: " + CasinoVIPManager.getBalance());

        if (spawn) {
            CasinoDebtScript.forceSpawnCollector();
            Console.showMessage("Debt collector fleet spawned.");
        }

        return CommandResult.SUCCESS;
    }
}
