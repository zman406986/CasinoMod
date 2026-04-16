package data.scripts.casino.shared;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

public final class CasinoSpriteCache {
    private static final SettingsAPI settings = Global.getSettings();
    private static final Map<String, SpriteAPI> cache = new HashMap<>();

    private CasinoSpriteCache() {}

    public static SpriteAPI getShipSprite(String hullId) {
        if (hullId == null || hullId.isEmpty()) return null;

        if (cache.containsKey(hullId)) {
            return cache.get(hullId);
        }

        try {
            ShipHullSpecAPI spec = settings.getHullSpec(hullId);
            if (spec == null) {
                cache.put(hullId, null);
                return null;
            }

            String spriteName = spec.getSpriteName();
            if (spriteName == null || spriteName.isEmpty()) {
                cache.put(hullId, null);
                return null;
            }

            SpriteAPI sprite = settings.getSprite(spriteName);
            cache.put(hullId, sprite);
            return sprite;
        } catch (Exception e) {
            cache.put(hullId, null);
            return null;
        }
    }

    public static void clear() {
        cache.clear();
    }
}