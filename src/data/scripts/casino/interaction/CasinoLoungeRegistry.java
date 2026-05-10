package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
//check cosmicon existence for collab features
public class CasinoLoungeRegistry {

    private static LoungeProvider provider = null;

    @SuppressWarnings("unused")
    public static void registerProvider(LoungeProvider p) {
        provider = p;
    }

    public static LoungeProvider getProvider() {
        return provider;
    }

    public static boolean isCosmiconEnabled() {
        return Global.getSettings().getModManager().isModEnabled("cosmicon_dice");
    }
}
