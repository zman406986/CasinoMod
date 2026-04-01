package data.scripts.casino.poker5;

import java.util.ArrayList;
import java.util.List;

import data.scripts.casino.cards.Card;

public class TableStateSnapshot {
    public final List<OpponentInfo> opponents;
    public final List<Card> communityCards;
    public final int pot;
    public final int currentBet;
    public final int buttonSeat;
    public final int currentActorSeat;
    public final PokerGame5.Round round;

    public TableStateSnapshot() {
        this.opponents = new ArrayList<>();
        this.communityCards = new ArrayList<>();
        this.pot = 0;
        this.currentBet = 0;
        this.buttonSeat = 0;
        this.currentActorSeat = 0;
        this.round = PokerGame5.Round.PREFLOP;
    }

    public TableStateSnapshot(List<OpponentInfo> opponents, List<Card> communityCards,
                              int pot, int currentBet, int buttonSeat, int currentActorSeat,
                              PokerGame5.Round round) {
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

    public static class OpponentInfo {
        public final int seatIndex;
        public final MultiPlayerPokerOpponentAI.Personality personality;
        public int stack;
        public int currentBet;
        public boolean isActive;
        public boolean hasActed;
        public boolean declaredAllIn;

        public OpponentInfo(int seatIndex, MultiPlayerPokerOpponentAI.Personality personality) {
            this.seatIndex = seatIndex;
            this.personality = personality;
            this.stack = 0;
            this.currentBet = 0;
            this.isActive = true;
            this.hasActed = false;
            this.declaredAllIn = false;
        }

        public OpponentInfo(int seatIndex, MultiPlayerPokerOpponentAI.Personality personality,
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