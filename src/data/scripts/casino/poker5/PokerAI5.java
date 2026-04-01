package data.scripts.casino.poker5;

public interface PokerAI5 {

    public static class AIResponse {
        public PokerGame5.Action action;
        public int raiseAmount;

        public AIResponse(PokerGame5.Action action) {
            this.action = action;
            this.raiseAmount = 0;
        }

        public AIResponse(PokerGame5.Action action, int raiseAmount) {
            this.action = action;
            this.raiseAmount = raiseAmount;
        }
    }

    public AIResponse decideAction(int playerIndex, PokerGame5.PokerState5 state);

    public void newHandStarted(int playerIndex, PokerGame5.PokerState5 state);

    public void recordAction(int playerIndex, String actionType);

    public void reset();
}