package data.scripts.casino.poker5;

public class PokerAI5Placeholder implements PokerAI5 {

    @Override
    public AIResponse decideAction(int playerIndex, PokerGame5.PokerState5 state) {
        int currentBet = 0;
        for (int i = 0; i < PokerGame5.NUM_PLAYERS; i++) {
            if (!state.foldedPlayers.contains(i) && state.bets[i] > currentBet) {
                currentBet = state.bets[i];
            }
        }
        int callAmount = currentBet - state.bets[playerIndex];

        if (callAmount <= 0) {
            return new AIResponse(PokerGame5.Action.CHECK);
        }

        if (callAmount <= state.stacks[playerIndex]) {
            return new AIResponse(PokerGame5.Action.CALL);
        }

        if (state.stacks[playerIndex] > 0) {
            return new AIResponse(PokerGame5.Action.ALL_IN);
        }

        return new AIResponse(PokerGame5.Action.FOLD);
    }

    @Override
    public void newHandStarted(int playerIndex, PokerGame5.PokerState5 state) {
    }

    @Override
    public void recordAction(int playerIndex, String actionType) {
    }

    @Override
    public void reset() {
    }
}