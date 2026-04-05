package data.scripts.casino.cards.poker5;

import data.scripts.casino.cards.pokerShared.PokerAction;

public interface PokerAI5 {

    class AIResponse {
        public PokerAction action;
        public int raiseAmount;

        public AIResponse(PokerAction action) {
            this.action = action;
            this.raiseAmount = 0;
        }

        public AIResponse(PokerAction action, int raiseAmount) {
            this.action = action;
            this.raiseAmount = raiseAmount;
        }
    }

    AIResponse decideAction(int playerIndex, PokerGame5.PokerState5 state);

    void newHandStarted(int playerIndex, PokerGame5.PokerState5 state);

    void recordAction(int playerIndex, String actionType);

    void reset();
}
