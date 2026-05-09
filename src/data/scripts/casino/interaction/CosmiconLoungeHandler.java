package data.scripts.casino.interaction;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.Strings;
import data.scripts.cosmicon.casino.CasinoIntegrationManager;

public class CosmiconLoungeHandler {

    private static final String KEY_BOSS_LAST_TIME = "$ipc_cosmicon_boss_last_time";

    private static final int BOSS_COST = 5000;
    private static final int CHALLENGE_COST = 10000;
    private static final float BOSS_COOLDOWN_DAYS = 30f;

    private final CasinoInteraction casino;

    public CosmiconLoungeHandler(CasinoInteraction casino) {
        this.casino = casino;
    }

    public void showLounge() {
        showLoungeMenu();
    }

    public void handle(String option) {
        InteractionDialogAPI dialog = casino.getDialog();
        MemoryAPI mem = Global.getSector().getPlayerMemoryWithoutUpdate();

        switch (option) {
            case "cosmicon_lounge" -> showLounge();
            case "lounge_back" -> casino.showMenu();
            case "lounge_boss" -> handleBossFight(dialog, mem);
            case "lounge_challenge" -> handleChallenge(dialog, mem);
            default -> {
                if (option.startsWith("lounge_")) {
                    showLoungeMenu();
                } else {
                    casino.showMenu();
                }
            }
        }
    }

    private void showLoungeMenu() {
        casino.options.clearOptions();
        MemoryAPI mem = Global.getSector().getPlayerMemoryWithoutUpdate();

        int hunterLevel = getTrashcanHunterLevel();
        if (hunterLevel > 0) {
            casino.textPanel.addPara(Strings.format("cosmicon_lounge.trashcan_hunter_greeting", hunterLevel), Color.CYAN);
        } else {
            casino.textPanel.addPara(Strings.get("cosmicon_lounge.welcome"), Color.CYAN);
        }

        if (!isTutorialComplete()) {
            casino.textPanel.addPara(Strings.get("cosmicon_lounge.tutorial_required"), Color.ORANGE);
            casino.options.addOption(Strings.get("cosmicon_lounge.back"), "lounge_back");
            return;
        }

        float cooldownRemaining = getBossCooldownRemaining(mem);
        if (cooldownRemaining > 0) {
            String cooldownText = Strings.format("cosmicon_lounge.boss_cooldown", cooldownRemaining);
            casino.textPanel.addPara(cooldownText, Color.GRAY);
        } else {
            casino.options.addOption(Strings.get("cosmicon_lounge.boss_cost"), "lounge_boss");
        }

        casino.options.addOption(Strings.get("cosmicon_lounge.challenge_cost"), "lounge_challenge");
        casino.options.addOption(Strings.get("cosmicon_lounge.back"), "lounge_back");
    }

    private void handleBossFight(InteractionDialogAPI dialog, MemoryAPI mem) {
        int balance = CasinoVIPManager.getBalance();
        if (balance < BOSS_COST) {
            casino.options.clearOptions();
            casino.textPanel.addPara(Strings.get("cosmicon_lounge.boss_insufficient"), Color.RED);
            casino.options.addOption(Strings.get("cosmicon_lounge.back"), "lounge_back");
            return;
        }

        CasinoVIPManager.addToBalance(-BOSS_COST);
        CampaignClockAPI clock = Global.getSector().getClock();
        mem.set(KEY_BOSS_LAST_TIME, clock.getTimestamp());

        Runnable backToLounge = () -> {
            dialog.setPlugin(casino);
            casino.options.clearOptions();
            showLoungeMenu();
        };

        startBossBattle(dialog, backToLounge);
    }

    private void handleChallenge(InteractionDialogAPI dialog, MemoryAPI mem) {
        int balance = CasinoVIPManager.getBalance();
        if (balance < CHALLENGE_COST) {
            casino.options.clearOptions();
            casino.textPanel.addPara(Strings.get("cosmicon_lounge.challenge_insufficient"), Color.RED);
            casino.options.addOption(Strings.get("cosmicon_lounge.back"), "lounge_back");
            return;
        }

        CasinoVIPManager.addToBalance(-CHALLENGE_COST);

        mem.unset(KEY_BOSS_LAST_TIME);

        Runnable backToLounge = () -> {
            dialog.setPlugin(casino);
            casino.options.clearOptions();
            showLoungeMenu();
        };

        startChallengeBattle(dialog, backToLounge);
    }

    private float getBossCooldownRemaining(MemoryAPI mem) {
        if (!mem.contains(KEY_BOSS_LAST_TIME)) return 0;
        long lastTime = mem.getLong(KEY_BOSS_LAST_TIME);
        if (lastTime == 0) return 0;
        CampaignClockAPI clock = Global.getSector().getClock();
        float elapsed = clock.getElapsedDaysSince(lastTime);
        if (elapsed >= BOSS_COOLDOWN_DAYS) return 0;
        return BOSS_COOLDOWN_DAYS - elapsed;
    }

    private boolean isTutorialComplete() {
        return CasinoIntegrationManager.isTutorialComplete();
    }

    private int getTrashcanHunterLevel() {
        return CasinoIntegrationManager.getTrashcanHunterLevel();
    }

    private void startBossBattle(InteractionDialogAPI dialog, Runnable onLeave) {
        CasinoIntegrationManager.startBossBattle(dialog, onLeave);
    }

    private void startChallengeBattle(InteractionDialogAPI dialog, Runnable onLeave) {
        CasinoIntegrationManager.startChallengeBattle(dialog, onLeave);
    }
}