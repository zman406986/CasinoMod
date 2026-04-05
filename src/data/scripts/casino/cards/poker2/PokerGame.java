package data.scripts.casino.cards.poker2;

import java.util.*;

import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.Deck;
import data.scripts.casino.cards.GameType;
import data.scripts.casino.cards.pokerShared.PokerAction;
import data.scripts.casino.cards.pokerShared.PokerAICommon;
import data.scripts.casino.cards.pokerShared.PokerHandEvaluator;
import data.scripts.casino.cards.pokerShared.PokerRound;
import data.scripts.casino.cards.pokerShared.PokerUtils;


public class PokerGame {

    public enum Dealer { PLAYER, OPPONENT }

    public enum CurrentPlayer { PLAYER, OPPONENT }

    public static class PokerState {
        public List<Card> playerHand;
        public List<Card> opponentHand;
        public List<Card> communityCards;
        public int pot;
        public int playerStack;
        public int opponentStack;
        public int playerBet;
        public int opponentBet;
        public Dealer dealer;
        public PokerRound round;
        public PokerHandEvaluator.HandRank playerHandRank;
        public PokerHandEvaluator.HandRank opponentHandRank;
        public CurrentPlayer currentPlayer;
        public int bigBlind;
        public CurrentPlayer folder;
        public int lastPotWon;
        public boolean playerHasActed;
        public boolean opponentHasActed;
        public boolean playerDeclaredAllIn;
        public boolean opponentDeclaredAllIn;
        
        public int displayPlayerBet;
        public int displayOpponentBet;
    }

    private final PokerState state;
    private final PokerOpponentAI ai;
    private Deck deck;
    private int bigBlindAmount;

    public PokerGame() {
        this(1000, 1000);
    }

    public PokerGame(int playerStack, int opponentStack) {
        ai = new PokerOpponentAI();
        state = new PokerState();

        int avgStack = (playerStack + opponentStack) / 2;

        this.bigBlindAmount = PokerUtils.calculateBigBlind(avgStack);

        state.playerStack = playerStack;
        state.opponentStack = opponentStack;
        state.bigBlind = bigBlindAmount;

        startNewHand();
    }
    
    @SuppressWarnings("unused")
    private PokerGame(boolean marker) {
        ai = new PokerOpponentAI();
        state = new PokerState();
        deck = new Deck(GameType.POKER);
        deck.shuffle();
    }
    
    public static String cardToString(Card card) {
        return PokerUtils.cardToString(card);
    }

    public static Card stringToCard(String str) {
        return PokerUtils.stringToCard(str);
    }

    public static PokerGame createSuspendedGame(
            int playerStack, int opponentStack, int bigBlind,
            int pot, int playerBet, int opponentBet,
            Dealer dealer, PokerRound round, CurrentPlayer currentPlayer,
            List<Card> playerHand,
            List<Card> opponentHand,
            List<Card> communityCards,
            boolean playerHasActed, boolean opponentHasActed,
            boolean playerDeclaredAllIn, boolean opponentDeclaredAllIn) {
        
        PokerGame game = new PokerGame(true);

        game.bigBlindAmount = bigBlind;

        game.state.playerStack = playerStack;
        game.state.opponentStack = opponentStack;
        game.state.bigBlind = bigBlind;
        game.state.pot = pot;
        game.state.playerBet = playerBet;
        game.state.opponentBet = opponentBet;
        game.state.dealer = dealer;
        game.state.round = round;
        game.state.currentPlayer = currentPlayer;
        game.state.playerHand = new ArrayList<>(playerHand);
        game.state.opponentHand = new ArrayList<>(opponentHand);
        game.state.communityCards = new ArrayList<>(communityCards);
        game.state.playerHasActed = playerHasActed;
        game.state.opponentHasActed = opponentHasActed;
        game.state.playerDeclaredAllIn = playerDeclaredAllIn;
        game.state.opponentDeclaredAllIn = opponentDeclaredAllIn;
        game.state.displayPlayerBet = playerBet;
        game.state.displayOpponentBet = opponentBet;
        
        game.evaluateHands();
        
        game.ai.newHandStarted(game.state.dealer == Dealer.OPPONENT);
        game.ai.resetBettingRoundTracking(pot);
        
        return game;
    }

    public PokerState getState() {
        return state;
    }

    public PokerOpponentAI getAI() {
        return ai;
    }

    public void startNewHand() {
        deck = new Deck(GameType.POKER);
        deck.shuffle();

        state.playerHand = new ArrayList<>();
        state.opponentHand = new ArrayList<>();
        state.communityCards = new ArrayList<>();

        state.playerHand.add(deck.draw());
        state.opponentHand.add(deck.draw());
        state.playerHand.add(deck.draw());
        state.opponentHand.add(deck.draw());



        state.playerBet = 0;
        state.opponentBet = 0;
        state.displayPlayerBet = 0;
        state.displayOpponentBet = 0;
        state.pot = 0;
        state.folder = null;
        state.playerHasActed = false;
        state.opponentHasActed = false;
        state.playerDeclaredAllIn = false;
        state.opponentDeclaredAllIn = false;

        state.dealer = state.dealer == Dealer.PLAYER ? Dealer.OPPONENT : Dealer.PLAYER;

        postBlinds();

        state.round = PokerRound.PREFLOP;
        // Heads-up poker: Small Blind (dealer) acts FIRST pre-flop
        // If player is dealer (SB), player acts first. If opponent is dealer (SB), opponent acts first.
        state.currentPlayer = getBigBlind() == Dealer.PLAYER ? CurrentPlayer.OPPONENT : CurrentPlayer.PLAYER;

        // Notify AI of new hand and position
        // AI is in position (acts last) when AI is the dealer
        ai.newHandStarted(state.dealer == Dealer.OPPONENT);

        // Reset betting round tracking for new hand
        ai.resetBettingRoundTracking(state.pot);

        }

    public Dealer getBigBlind() {
        return state.dealer == Dealer.PLAYER ? Dealer.OPPONENT : Dealer.PLAYER;
    }

    private void postBlinds() {
        if (state.dealer == Dealer.PLAYER) {
            // Small blind (player) - ensure doesn't go negative
            int playerSmallBlind = Math.min(bigBlindAmount / 2, state.playerStack);
            state.playerBet = playerSmallBlind;
            state.displayPlayerBet = playerSmallBlind;
            state.playerStack -= playerSmallBlind;
            
            // Big blind (opponent) - ensure doesn't go negative
            int opponentBigBlind = Math.min(bigBlindAmount, state.opponentStack);
            state.opponentBet = opponentBigBlind;
            state.displayOpponentBet = opponentBigBlind;
            state.opponentStack -= opponentBigBlind;
        } else {
            // Small blind (opponent) - ensure doesn't go negative
            int opponentSmallBlind = Math.min(bigBlindAmount / 2, state.opponentStack);
            state.opponentBet = opponentSmallBlind;
            state.displayOpponentBet = opponentSmallBlind;
            state.opponentStack -= opponentSmallBlind;
            
            // Big blind (player) - ensure doesn't go negative
            int playerBigBlind = Math.min(bigBlindAmount, state.playerStack);
            state.playerBet = playerBigBlind;
            state.displayPlayerBet = playerBigBlind;
            state.playerStack -= playerBigBlind;
        }
        state.pot = state.playerBet + state.opponentBet;
    }

    public void processPlayerAction(PokerAction action, int raiseAmount) {
        switch (action) {
            case RAISE:
                ai.recordPlayerAction("RAISE");
                break;
            case ALL_IN:
                ai.recordPlayerAction("ALL_IN");
                break;
            case CALL:
                ai.recordPlayerAction("CALL");
                break;
            case CHECK:
                ai.recordPlayerAction("CHECK");
                break;
            case FOLD:
                ai.recordPlayerAction("FOLD");
                break;
        }

        switch (action) {
            case FOLD:
                state.round = PokerRound.SHOWDOWN;
                state.folder = CurrentPlayer.PLAYER;
                break;
            case CHECK:
                break;
            case CALL:
                int callAmount = state.opponentBet - state.playerBet;
                state.playerStack -= callAmount;
                state.playerBet = state.opponentBet;
                state.displayPlayerBet = state.opponentBet;
                state.pot += callAmount;
                break;
            case RAISE:
                if (state.opponentDeclaredAllIn) {
                    int callAmt = state.opponentBet - state.playerBet;
                    state.playerStack -= callAmt;
                    state.playerBet = state.opponentBet;
                    state.displayPlayerBet = state.opponentBet;
                    state.pot += callAmt;
                    break;
                }
                state.opponentHasActed = false;
                int totalBet = raiseAmount;
                int raiseAmountActual = Math.min(totalBet - state.playerBet, state.playerStack);
                totalBet = state.playerBet + raiseAmountActual;
                
                if (raiseAmountActual >= state.playerStack) {
                    state.playerDeclaredAllIn = true;
                }
                
                applyPlayerBet(totalBet, raiseAmountActual);
                break;
            case ALL_IN:
                state.playerDeclaredAllIn = true;
                state.opponentHasActed = false;
                applyPlayerBet(state.playerBet + state.playerStack, state.playerStack);
                break;
        }

        ai.trackPlayerAction(
            action == PokerAction.RAISE || action == PokerAction.ALL_IN,
            action == PokerAction.FOLD,
            action == PokerAction.CHECK,
            state.round == PokerRound.PREFLOP,
            action == PokerAction.CALL || action == PokerAction.RAISE || action == PokerAction.ALL_IN
        );

        state.playerHasActed = true;

        if (state.round != PokerRound.SHOWDOWN) {
            state.currentPlayer = CurrentPlayer.OPPONENT;
            checkRoundProgression();
        }
    }
    
    private void applyPlayerBet(int totalBet, int betAmount) {
        int playerContribution = totalBet;
        int opponentCanMatch = state.opponentBet + state.opponentStack;
        
        if (playerContribution > opponentCanMatch) {
            int actualBet = opponentCanMatch - state.playerBet;
            int excess = betAmount - actualBet;
            
            state.playerStack -= actualBet;
            if (!state.playerDeclaredAllIn) {
                state.playerStack += excess;
            }
            state.playerBet = opponentCanMatch;
            state.displayPlayerBet = opponentCanMatch;
            state.pot += actualBet;
        } else {
            state.playerStack -= betAmount;
            state.playerBet = totalBet;
            state.displayPlayerBet = totalBet;
            state.pot += betAmount;
        }
    }

    public PokerAICommon.AIResponse getOpponentAction() {
        int currentBetToCall = state.playerBet - state.opponentBet;
        if (state.playerDeclaredAllIn) {
            return ai.decideAllInResponse(state.opponentHand, state.communityCards, currentBetToCall, state.pot);
        }
        return ai.decide(state.opponentHand, state.communityCards, currentBetToCall, state.pot, state.opponentStack);
    }

    public void processOpponentAction(PokerAICommon.AIResponse response) {
        switch (response.action) {
            case RAISE:
                ai.recordAIAction("RAISE");
                break;
            case CALL:
                ai.recordAIAction("CALL");
                break;
            case CHECK:
                ai.recordAIAction("CHECK");
                break;
            case FOLD:
                ai.recordAIAction("FOLD");
                break;
        }

        switch (response.action) {
            case FOLD:
                state.round = PokerRound.SHOWDOWN;
                state.folder = CurrentPlayer.OPPONENT;
                break;
            case CHECK:
                break;
            case CALL:
                int callAmount = state.playerBet - state.opponentBet;
                state.opponentStack -= callAmount;
                state.opponentBet = state.playerBet;
                state.displayOpponentBet = state.playerBet;
                state.pot += callAmount;
                break;
            case RAISE:
                int totalBet = state.opponentBet + response.raiseAmount;
                int raiseAmountActual = Math.min(totalBet - state.opponentBet, state.opponentStack);
                totalBet = state.opponentBet + raiseAmountActual;
                
                if (raiseAmountActual >= state.opponentStack) {
                    state.opponentDeclaredAllIn = true;
                }
                
                applyOpponentBet(totalBet, raiseAmountActual);
                
                if (state.playerStack <= 0) {
                    state.playerHasActed = true;
                } else {
                    state.playerHasActed = false;
                }
                break;
        }

        state.opponentHasActed = true;

        if (state.round != PokerRound.SHOWDOWN) {
            state.currentPlayer = CurrentPlayer.PLAYER;
            checkRoundProgression();
        }
    }
    
    private void applyOpponentBet(int totalBet, int betAmount) {
        int opponentContribution = totalBet;
        int playerCanMatch = state.playerBet + state.playerStack;
        
        if (opponentContribution > playerCanMatch) {
            int actualBet = playerCanMatch - state.opponentBet;
            int excess = betAmount - actualBet;
            
            state.opponentStack -= actualBet;
            if (!state.opponentDeclaredAllIn) {
                state.opponentStack += excess;
            }
            state.opponentBet = playerCanMatch;
            state.displayOpponentBet = playerCanMatch;
            state.pot += actualBet;
            ai.addCommittedThisRound(actualBet);
        } else {
            state.opponentStack -= betAmount;
            state.opponentBet = totalBet;
            state.displayOpponentBet = totalBet;
            state.pot += betAmount;
            ai.addCommittedThisRound(betAmount);
        }
    }

private void checkRoundProgression() {
        if (state.playerBet == state.opponentBet && state.playerHasActed && state.opponentHasActed) {
            if (state.playerStack == 0 || state.opponentStack == 0 || 
                state.playerDeclaredAllIn || state.opponentDeclaredAllIn) {
                while (state.round != PokerRound.SHOWDOWN) {
                    advanceRound();
                }
            } else {
                advanceRound();
            }
        }
    }

    private void advanceRound() {
        state.playerBet = 0;
        state.opponentBet = 0;
        state.playerHasActed = false;
        state.opponentHasActed = false;

        ai.resetCommittedChips();
        ai.resetBettingRoundTracking(state.pot);

        PokerRound fromRound = state.round;
        switch (state.round) {
            case PREFLOP -> state.round = PokerRound.FLOP;
            case FLOP -> state.round = PokerRound.TURN;
            case TURN -> state.round = PokerRound.RIVER;
            case RIVER -> state.round = PokerRound.SHOWDOWN;
            case SHOWDOWN -> { return; }
        }

        PokerUtils.dealCommunityCards(fromRound, deck, state.communityCards);

        evaluateHands();

        if (state.round != PokerRound.SHOWDOWN) {
            state.currentPlayer = state.dealer == Dealer.PLAYER ? CurrentPlayer.PLAYER : CurrentPlayer.OPPONENT;
        }
    }

    private void evaluateHands() {
        if (state.communityCards.size() >= 3) {
            PokerHandEvaluator.HandScore playerScore = PokerHandEvaluator.evaluate(state.playerHand, state.communityCards);
            PokerHandEvaluator.HandScore opponentScore = PokerHandEvaluator.evaluate(state.opponentHand, state.communityCards);
            state.playerHandRank = playerScore.rank;
            state.opponentHandRank = opponentScore.rank;
        } else {
            state.playerHandRank = null;
            state.opponentHandRank = null;
        }
    }
}
