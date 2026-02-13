package data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * Manages casino-specific background music.
 * Suspends default music and plays casino theme, restoring original music on exit.
 */
public class CasinoMusicPlugin {
    
    private static final String CASINO_MUSIC_ID = "casino_theme";
    private static final String CASINO_MUSIC_MEMORY_KEY = "$casinoMusicPlaying";
    
    private static String originalMusicId = null;
    private static boolean wasMusicPlaying = false;
    
    public static void startCasinoMusic() {
        originalMusicId = Global.getSoundPlayer().getCurrentMusicId();
        wasMusicPlaying = originalMusicId != null && !originalMusicId.equals("nothing") && !originalMusicId.equals("null");
        
        Global.getSoundPlayer().setSuspendDefaultMusicPlayback(true);
        Global.getSoundPlayer().playCustomMusic(1, 1, CASINO_MUSIC_ID, true);
        
        MemoryAPI globalMemory = Global.getSector().getMemory();
        globalMemory.set(CASINO_MUSIC_MEMORY_KEY, true);
    }
    
    public static void stopCasinoMusic() {
        Global.getSoundPlayer().pauseCustomMusic();
        Global.getSoundPlayer().setSuspendDefaultMusicPlayback(false);
        
        if (originalMusicId != null && !originalMusicId.equals("nothing") && !originalMusicId.equals("null") && wasMusicPlaying) {
            try {
                Global.getSoundPlayer().playCustomMusic(1, 1, originalMusicId, true);
            } catch (Exception e) {
                Global.getSoundPlayer().restartCurrentMusic();
            }
        } else {
            Global.getSoundPlayer().restartCurrentMusic();
        }
        
        MemoryAPI globalMemory = Global.getSector().getMemory();
        globalMemory.unset(CASINO_MUSIC_MEMORY_KEY);
    }
    
    public static boolean isCasinoMusicPlaying() {
        return Global.getSector().getMemory().getBoolean(CASINO_MUSIC_MEMORY_KEY);
    }
}
