package data.scripts.casino.cards.pokerShared;

import java.awt.Color;

import data.scripts.casino.Strings;
import data.scripts.casino.cards.CardFlipAnimation;

public final class PokerUIUtils {
    private PokerUIUtils() {}

    public static boolean notContainsInt(int[] arr, int len, int val) {
        for (int i = 0; i < len; i++) {
            if (arr[i] == val) return false;
        }
        return true;
    }

    public static final Color COLOR_ROUND_PREFLOP = new Color(150, 150, 200);
    public static final Color COLOR_ROUND_FLOP = new Color(100, 200, 100);
    public static final Color COLOR_ROUND_TURN = new Color(200, 200, 100);
    public static final Color COLOR_ROUND_RIVER = new Color(200, 150, 100);
    public static final Color COLOR_ROUND_SHOWDOWN = new Color(255, 200, 50);

    public static String getRoundName(PokerRound round) {
        return switch (round) {
            case PREFLOP -> Strings.get("poker_rounds.preflop");
            case FLOP -> Strings.get("poker_rounds.flop");
            case TURN -> Strings.get("poker_rounds.turn");
            case RIVER -> Strings.get("poker_rounds.river");
            case SHOWDOWN -> Strings.get("poker_rounds.showdown");
        };
    }

    public static Color getRoundColor(PokerRound round) {
        return switch (round) {
            case PREFLOP -> COLOR_ROUND_PREFLOP;
            case FLOP -> COLOR_ROUND_FLOP;
            case TURN -> COLOR_ROUND_TURN;
            case RIVER -> COLOR_ROUND_RIVER;
            case SHOWDOWN -> COLOR_ROUND_SHOWDOWN;
        };
    }

    public static void resetAnimations(CardFlipAnimation[] playerAnimations,
                                        CardFlipAnimation[][] opponentAnimations,
                                        CardFlipAnimation[] communityAnimations) {
        for (CardFlipAnimation anim : playerAnimations) {
            anim.reset();
        }
        if (opponentAnimations != null) {
            for (CardFlipAnimation[] row : opponentAnimations) {
                for (CardFlipAnimation anim : row) {
                    anim.reset();
                }
            }
        }
        for (CardFlipAnimation anim : communityAnimations) {
            anim.reset();
        }
    }

    public static void triggerPlayerCardAnimations(CardFlipAnimation[] animations,
                                                    int cardCount, float staggerBase) {
        for (int i = 0; i < cardCount && i < animations.length; i++) {
            animations[i].triggerFlip(i * staggerBase);
        }
    }

    public static void triggerCommunityAnimations(CardFlipAnimation[] animations,
                                                   int fromCount, int toCount, float staggerBase) {
        for (int i = fromCount; i < toCount && i < animations.length; i++) {
            if (animations[i].phase == CardFlipAnimation.Phase.HIDDEN) {
                animations[i].triggerFlip((i - fromCount) * staggerBase);
            }
        }
    }

    public static void triggerOpponentAnimations(CardFlipAnimation[] animations,
                                                  int cardCount, float staggerBase) {
        for (int i = 0; i < cardCount && i < animations.length; i++) {
            if (animations[i].phase == CardFlipAnimation.Phase.HIDDEN) {
                animations[i].triggerFlip(i * staggerBase);
            }
        }
    }
}