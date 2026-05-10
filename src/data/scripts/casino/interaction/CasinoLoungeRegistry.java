package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;

public class CasinoLoungeRegistry {

    public static boolean isCosmiconEnabled() {
        return Global.getSettings().getModManager().isModEnabled("cosmicon_dice");
    }

    public static LoungeProvider createProvider() {
        return new data.scripts.cosmicon.casino.CosmiconLoungeProvider();
    }
}
