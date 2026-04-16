package data.scripts.casino.cards;

import data.scripts.casino.shared.GLColorUtils;

public class CardFlipAnimation {
    public enum Phase { HIDDEN, FLIPPING, REVEALED }
        
    public Phase phase = Phase.HIDDEN;
    public float progress = 0f;
    public float delay = 0f;
    public boolean triggered = false;
    
    public static final float FLIP_DURATION = 0.4f;
    public static final float STAGGER_DELAY = 0.08f;

    public boolean isRevealed() { return phase == Phase.REVEALED; }
    public boolean shouldShowBack() { 
        return phase == Phase.HIDDEN || (phase == Phase.FLIPPING && progress < 0.5f); 
    }
    
    public float getWidthScale() {
        if (phase != Phase.FLIPPING) return 1f;
        return (float) Math.abs(Math.cos(progress * Math.PI));
    }
    
    public void advance(float amount) {
        amount = GLColorUtils.capDelta(amount);

        if (phase == Phase.HIDDEN && triggered) {
            if (delay > 0) {
                delay -= amount;
                if (delay <= 0) {
                    delay = 0;
                    phase = Phase.FLIPPING;
                }
            } else {
                phase = Phase.FLIPPING;
            }
        }
        if (phase == Phase.FLIPPING) {
            progress += amount / FLIP_DURATION;
            if (progress >= 1f) {
                progress = 1f;
                phase = Phase.REVEALED;
            }
        }
    }
    
    public void triggerFlip(float staggerDelay) {
        this.delay = staggerDelay;
        this.progress = 0f;
        this.phase = Phase.HIDDEN;
        this.triggered = true;
    }
    
    public void reset() {
        phase = Phase.HIDDEN;
        progress = 0f;
        delay = 0f;
        triggered = false;
    }
}
