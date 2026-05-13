package data.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import data.scripts.casino.CasinoDebtScript;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.gacha.CasinoGachaManager;
import data.scripts.casino.gacha.CasinoGachaManager.GachaData;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class CasinoStatus implements BaseCommand {

    @Override
    public CommandResult runCommand(@NotNull String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        int balance = CasinoVIPManager.getBalance();
        int vipDays = CasinoVIPManager.getDaysRemaining();
        int debt = CasinoVIPManager.getDebt();
        int creditCeiling = CasinoVIPManager.getCreditCeiling();
        int maxDebt = CasinoVIPManager.getMaxDebt();
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        int cumulativeTopup = CasinoVIPManager.getCumulativeTopupAmount();
        int cumulativeVIP = CasinoVIPManager.getCumulativeVIPPurchases();

        Console.showMessage("=== Casino Status ===");
        Console.showMessage("Stargem Balance: " + balance);
        Console.showMessage("VIP Days Remaining: " + vipDays);
        Console.showMessage("Debt: " + debt);
        Console.showMessage("Credit Ceiling: " + creditCeiling);
        Console.showMessage("Max Debt: " + maxDebt);
        Console.showMessage("Available Credit: " + availableCredit);
        Console.showMessage("Cumulative Topup: " + cumulativeTopup);
        Console.showMessage("Cumulative VIP Purchases: " + cumulativeVIP);

        Console.showMessage("");
        Console.showMessage("--- Gacha ---");
        CasinoGachaManager manager = new CasinoGachaManager();
        GachaData gacha = manager.getData();
        Console.showMessage("5★ Pity: " + gacha.pity5);
        Console.showMessage("4★ Pity: " + gacha.pity4);
        Console.showMessage("Guaranteed Featured 5★: " + gacha.guaranteedFeatured5);
        Console.showMessage("Guaranteed Featured 4★: " + gacha.guaranteedFeatured4);
        if (gacha.featuredCapital != null) {
            Console.showMessage("Featured Capital: " + gacha.featuredCapital);
        }
        if (!gacha.featuredCruisers.isEmpty()) {
            Console.showMessage("Featured Cruisers: " + String.join(", ", gacha.featuredCruisers));
        }

        Console.showMessage("");
        Console.showMessage("--- Debt Collector ---");
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        String collectorState = memory.getString("$ipc_debt_collector_state");
        if (collectorState == null || collectorState.isEmpty()) collectorState = "none";
        Console.showMessage("State: " + collectorState);
        boolean active = CasinoDebtScript.isCollectorActive();
        boolean pending = CasinoDebtScript.isCollectorPending();
        Console.showMessage("Active: " + active + "  Pending: " + pending);

        return CommandResult.SUCCESS;
    }
}
