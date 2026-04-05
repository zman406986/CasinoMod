package data.scripts.casino;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;

public class Strings {
    private static final Logger log = Global.getLogger(Strings.class);
    private static final String STRINGS_PATH = "data/config/strings.json";
    private static JSONObject strings = null;

    static { load();}
    private static void load() {
        try {
            strings = Global.getSettings().loadJSON(STRINGS_PATH, CasinoConfig.MOD_ID);
            log.info("Casino strings loaded successfully");
        } catch (IOException | JSONException e) {
            throw new RuntimeException("Strings from " + STRINGS_PATH + " could not be loaded.");
        }
    }

    public static String get(String key) {
        if (strings == null) { load();}
        
        final String[] parts = key.split("\\.");
        try {
            JSONObject current = strings;
            for (int i = 0; i < parts.length - 1; i++) {
                current = current.getJSONObject(parts[i]);
            }
            return current.getString(parts[parts.length - 1]);

        } catch (JSONException e) {
            throw new MissingResourceException("Missing translation for key: " + key, "Strings", key);
        }
    }

    public static String format(String key, Object... args) {
        return String.format(get(key), args);
    }

    public static boolean has(String key) {
        if (strings == null) { load();}
        
        final String[] parts = key.split("\\.");
        try {
            JSONObject current = strings;
            for (int i = 0; i < parts.length - 1; i++) {
                if (!current.has(parts[i])) return false;
                current = current.getJSONObject(parts[i]);
            }
            return current.has(parts[parts.length - 1]);

        } catch (JSONException e) {
            return false;
        }
    }

    public static List<String> getList(String key) {
        if (strings == null) { load();}
        
        final String[] parts = key.split("\\.");
        try {
            JSONObject current = strings;
            for (int i = 0; i < parts.length - 1; i++) {
                current = current.getJSONObject(parts[i]);
            }
            
            final JSONArray array = current.getJSONArray(parts[parts.length - 1]);
            final List<String> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                result.add(array.getString(i));
            }
            return result;

        } catch (JSONException e) {
            throw new MissingResourceException("Missing translation for key: " + key, "Strings", key);
        }
    }
}
