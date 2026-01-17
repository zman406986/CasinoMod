package data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;


public class CasinoMusicPlugin {
    
    private static final String CASINO_MUSIC_ID = "casino_theme";
    private static final String CASINO_MUSIC_MEMORY_KEY = "$casinoMusicPlaying";
    
    // Store original music state when casino music starts
    private static String originalMusicId = null;
    private static boolean wasMusicPlaying = false;
    
    public static void startCasinoMusic() {
        // Save current music state
        originalMusicId = Global.getSoundPlayer().getCurrentMusicId();
        wasMusicPlaying = !originalMusicId.equals("nothing") && !originalMusicId.equals("null");
        
        // Suspend default music playback and play casino music
        Global.getSoundPlayer().setSuspendDefaultMusicPlayback(true);
        Global.getSoundPlayer().playCustomMusic(1, 1, CASINO_MUSIC_ID, true);
        
        // Set memory flag
        MemoryAPI globalMemory = Global.getSector().getMemory();
        globalMemory.set(CASINO_MUSIC_MEMORY_KEY, true);
    }
    
    public static void stopCasinoMusic() {
        // Stop casino music
        Global.getSoundPlayer().pauseCustomMusic();
        
        // Resume default music playback
        Global.getSoundPlayer().setSuspendDefaultMusicPlayback(false);
        
        // Restore previous music if there was one and it's valid
        if (originalMusicId != null && !originalMusicId.equals("nothing") && !originalMusicId.equals("null") && wasMusicPlaying) {
            try {
                Global.getSoundPlayer().playCustomMusic(1, 1, originalMusicId, true);
            } catch (Exception e) {
                // If there's an issue restoring the original music, just restart current music
                Global.getSoundPlayer().restartCurrentMusic();
            }
        } else {
            Global.getSoundPlayer().restartCurrentMusic();
        }
        
        // Clear memory flag
        MemoryAPI globalMemory = Global.getSector().getMemory();
        globalMemory.unset(CASINO_MUSIC_MEMORY_KEY);
    }
    
    public static boolean isCasinoMusicPlaying() {
        // Check memory flag as a proxy for whether casino music is playing
        return Global.getSector().getMemory().getBoolean(CASINO_MUSIC_MEMORY_KEY);
    }
}