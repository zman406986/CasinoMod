package data.scripts.casino.cards.pokerShared;

import java.util.List;

import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.GameType;

public class PokerAIUtils {

    public enum HandCategory { PREMIUM, STRONG, PLAYABLE, WEAK }

    public static HandCategory classifyPreflopHand(List<Card> holeCards) {
        if (holeCards == null || holeCards.size() < 2) return HandCategory.WEAK;
        Card c1 = holeCards.get(0);
        Card c2 = holeCards.get(1);
        int v1 = Math.max(c1.rank().getValue(GameType.POKER), c2.rank().getValue(GameType.POKER));
        int v2 = Math.min(c1.rank().getValue(GameType.POKER), c2.rank().getValue(GameType.POKER));
        boolean suited = (c1.suit() == c2.suit());

        if (v1 == v2) {
            if (v1 >= 11) return HandCategory.PREMIUM;
            if (v1 >= 9) return HandCategory.STRONG;
            return HandCategory.PLAYABLE;
        }

        if (v1 == 14 && v2 == 13) return HandCategory.PREMIUM;

        if ((v1 == 14 && v2 >= 11 && suited) ||
            (v1 == 13 && v2 == 12 && suited) ||
            (v1 == 14 && v2 == 12)) return HandCategory.STRONG;

        if (suited && ((v1 == 14) || (v1 == 13 && v2 >= 10) ||
            (v1 - v2 == 1 && v1 >= 10))) return HandCategory.PLAYABLE;

        return HandCategory.WEAK;
    }

    public static boolean shouldCallPreflopAllIn(HandCategory handStrength, float potOdds, float bbStack) {
        if (handStrength == HandCategory.PREMIUM) return true;
        if (handStrength == HandCategory.STRONG) {
            if (potOdds >= 0.40f) return true;
            if (bbStack < 8) return true;
            return false;
        }
        if (handStrength == HandCategory.PLAYABLE) {
            if (potOdds >= 0.45f) return true;
            if (bbStack < 5) return true;
            return false;
        }
        return false;
    }

    public static float calculateCallEV(float equity, int potSize, int betToCall) {
        float winAmount = potSize + betToCall;
        return equity * winAmount - (1 - equity) * betToCall;
    }

    public static float calculateRaiseEVWithCommitment(float equity, int potSize, int currentBet, int raiseAmount, float foldProbability, int committedThisRound) {
        int totalInvestment = currentBet + raiseAmount + committedThisRound;
        int finalPot = potSize + currentBet + raiseAmount;
        float evWhenCalled = equity * finalPot - (1 - equity) * totalInvestment;
        return foldProbability * potSize + (1 - foldProbability) * evWhenCalled;
    }

    public static float calculateRaiseEVMultiWay(float equity, int potSize, int currentBet, int raiseAmount, float foldProbability, int opponentCount) {
        int totalInvestment = currentBet + raiseAmount;
        int finalPot = potSize + currentBet + raiseAmount;
        float adjustedFoldProb = (float) Math.pow(foldProbability, opponentCount);
        float evWhenCalled = equity * finalPot - (1 - equity) * totalInvestment;
        return adjustedFoldProb * potSize + (1 - adjustedFoldProb) * evWhenCalled;
    }

    public static float calculateBluffEV(float foldProbability, int potSize, int bluffAmount) {
        return foldProbability * potSize - (1 - foldProbability) * bluffAmount;
    }

    public static float calculateBluffEVMultiWay(float foldProbability, int potSize, int bluffAmount, int opponentCount) {
        float adjustedFoldProb = (float) Math.pow(foldProbability, opponentCount);
        return adjustedFoldProb * potSize - (1 - adjustedFoldProb) * bluffAmount;
    }

    public static int countOuts(List<Card> holeCards, List<Card> communityCards) {
        int outs = 0;
        int[] suitCounts = new int[4];
        for (Card c : holeCards) suitCounts[c.suit().ordinal()]++;
        for (Card c : communityCards) suitCounts[c.suit().ordinal()]++;
        for (int count : suitCounts) {
            if (count == 4) outs += 9;
        }

        boolean[] rankPresent = new boolean[15];
        for (Card c : holeCards) rankPresent[c.rank().getValue(GameType.POKER)] = true;
        for (Card c : communityCards) rankPresent[c.rank().getValue(GameType.POKER)] = true;
        rankPresent[1] = rankPresent[14];

        int maxSeq = 0, currentSeq = 0;
        for (int r = 1; r <= 14; r++) {
            if (rankPresent[r]) {
                currentSeq++;
                maxSeq = Math.max(maxSeq, currentSeq);
            } else {
                currentSeq = 0;
            }
        }

        if (maxSeq == 4) outs += 8;
        else if (maxSeq == 3) outs += 4;
        return outs;
    }

    public static boolean isWetBoard(List<Card> communityCards) {
        int[] suitCounts = new int[4];
        for (Card c : communityCards) suitCounts[c.suit().ordinal()]++;
        for (int count : suitCounts) {
            if (count >= 3) return true;
        }

        boolean[] rankPresent = new boolean[15];
        for (Card c : communityCards) rankPresent[c.rank().getValue(GameType.POKER)] = true;

        int maxSeq = 0, currentSeq = 0;
        for (int r = 2; r <= 14; r++) {
            if (rankPresent[r]) {
                currentSeq++;
                maxSeq = Math.max(maxSeq, currentSeq);
            } else {
                currentSeq = 0;
            }
        }
        return maxSeq >= 3;
    }
}