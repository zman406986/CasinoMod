package data.scripts.casino.cards.poker5;

import java.util.List;

import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.pokerShared.PokerAICommon;
import data.scripts.casino.cards.pokerShared.PokerRound;

public class TableStateSnapshot {
    public final List<OpponentInfo> opponents;
    public final List<Card> communityCards;
    public final int pot;
    public final int currentBet;
    public final int buttonSeat;
    public final int currentActorSeat;
    public final PokerRound round;

    public TableStateSnapshot(List<OpponentInfo> opponents, List<Card> communityCards,
                              int pot, int currentBet, int buttonSeat, int currentActorSeat,
                              PokerRound round) {
        this.opponents = opponents;
        this.communityCards = communityCards;
        this.pot = pot;
        this.currentBet = currentBet;
        this.buttonSeat = buttonSeat;
        this.currentActorSeat = currentActorSeat;
        this.round = round;
    }

    public int getActiveOpponentCount() {
        int count = 0;
        for (OpponentInfo opp : opponents) {
            if (opp.isActive) count++;
        }
        return count;
    }

    public int getBetToCall(int myCurrentBet) {
        return currentBet - myCurrentBet;
    }

    public int countPlayersActingAfter(Position myPosition) {
        int count = 0;
        for (OpponentInfo opp : opponents) {
            if (!opp.isActive) continue;
            Position oppPos = opp.getPosition(buttonSeat);
            if (oppPos.isInPositionVs(myPosition)) {
                count++;
            }
        }
        return count;
    }

    public int countPlayersActingBefore(Position myPosition) {
        int count = 0;
        for (OpponentInfo opp : opponents) {
            if (!opp.isActive) continue;
            Position oppPos = opp.getPosition(buttonSeat);
            if (myPosition.isInPositionVs(oppPos)) {
                count++;
            }
        }
        return count;
    }

    public OpponentInfo findLikelyRaiser() {
        OpponentInfo raiser = null;
        int maxBet = 0;
        for (OpponentInfo opp : opponents) {
            if (!opp.isActive) continue;
            if (opp.currentBet > maxBet) {
                maxBet = opp.currentBet;
                raiser = opp;
            }
        }
        return raiser;
    }

    public boolean isStealAttempt(OpponentInfo raiser) {
        if (raiser == null) return false;
        Position raiserPos = raiser.getPosition(buttonSeat);
        return raiserPos.isLatePosition();
    }

    public static class OpponentInfo {
        public final int seatIndex;
        public final PokerAICommon.Personality personality;
        public int stack;
        public int currentBet;
        public boolean isActive;
        public boolean hasActed;
        public boolean declaredAllIn;

        public OpponentInfo(int seatIndex, PokerAICommon.Personality personality,
                           int stack, int currentBet, boolean isActive, boolean hasActed, boolean declaredAllIn) {
            this.seatIndex = seatIndex;
            this.personality = personality;
            this.stack = stack;
            this.currentBet = currentBet;
            this.isActive = isActive;
            this.hasActed = hasActed;
            this.declaredAllIn = declaredAllIn;
        }

        public Position getPosition(int buttonSeat) {
            return Position.fromSeatIndex(seatIndex, buttonSeat);
        }
    }
}
