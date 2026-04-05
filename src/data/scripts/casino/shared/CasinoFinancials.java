package data.scripts.casino.shared;

import java.awt.Color;

import com.fs.starfarer.api.campaign.TextPanelAPI;

import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.Strings;

public final class CasinoFinancials {
    private CasinoFinancials() {}

    public static void displayFinancialInfo(TextPanelAPI textPanel) {
        int currentBalance = CasinoVIPManager.getBalance();
        int creditCeiling = CasinoVIPManager.getCreditCeiling();
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        int daysRemaining = CasinoVIPManager.getDaysRemaining();

        textPanel.addPara(Strings.get("financial_status.header"), Color.CYAN);

        Color balanceColor = currentBalance >= 0 ? Color.GREEN : Color.RED;
        textPanel.addPara(Strings.format("financial_status.balance", currentBalance), balanceColor);

        textPanel.addPara(Strings.format("financial_status.credit_ceiling", creditCeiling), Color.GRAY);
        textPanel.addPara(Strings.format("financial_status.available_credit", availableCredit), Color.YELLOW);

        if (daysRemaining > 0) {
            textPanel.addPara(Strings.format("financial_status.vip_days", daysRemaining), Color.CYAN);
        }

        textPanel.addPara(Strings.get("financial_status.divider"), Color.CYAN);
    }
}
