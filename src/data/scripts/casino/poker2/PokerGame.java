package data.scripts.casino.poker2;

import java.util.*;

import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.Deck;
import data.scripts.casino.cards.GameType;
import data.scripts.casino.cards.Rank;
import data.scripts.casino.cards.Suit;


public class PokerGame {

    public enum Action { FOLD, CHECK, CALL, RAISE, ALL_IN }

    public enum Dealer { PLAYER, OPPONENT }

    public enum Round { PREFLOP, FLOP, TURN, RIVER, SHOWDOWN }

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
        public Round round;
        public PokerGameLogic.HandRank playerHandRank;
        public PokerGameLogic.HandRank opponentHandRank;
        public CurrentPlayer currentPlayer;
        public int bigBlind;
        public CurrentPlayer folder = null;
        public int lastPotWon = 0;
        public boolean playerHasActed = false;
        public boolean opponentHasActed = false;
        public boolean playerDeclaredAllIn = false;
        public boolean opponentDeclaredAllIn = false;
        
        // Display bet values - persist across round transitions for UI display
        // These track what each player has bet in the current betting round
        // Updated when bets change, reset only when new betting action starts
        public int displayPlayerBet = 0;
        public int displayOpponentBet = 0;
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

        this.bigBlindAmount = calculateBigBlind(avgStack);

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
    
    public static PokerGame createSuspendedGame(
            int playerStack, int opponentStack, int bigBlind,
            int pot, int playerBet, int opponentBet,
            Dealer dealer, Round round, CurrentPlayer currentPlayer,
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
    
    public static String cardToString(Card card) {
        if (card == null) return "";
        return card.value() + "-" + card.suit().name();
    }
    
    private static final Rank[] RANK_BY_VALUE = new Rank[15];
    static {
        for (Rank r : Rank.values()) {
            RANK_BY_VALUE[r.getValue(GameType.POKER)] = r;
        }
    }
    
    public static Card stringToCard(String str) {
        if (str == null || str.isEmpty()) return null;
        String[] parts = str.split("-");
        if (parts.length != 2) return null;
        int rankValue = Integer.parseInt(parts[0]);
        Suit suit = Suit.valueOf(parts[1]);
        if (rankValue >= 2 && rankValue <= 14) {
            return new Card(RANK_BY_VALUE[rankValue], suit, GameType.POKER);
        }
        return null;
    }

    /**
     * Calculate big blind amount for 100 BB stack depth.
     * BB = stack / 100, rounded to nearest 10, minimum 10.
     */
    private static int calculateBigBlind(int avgStack) {
        int calculatedBB = avgStack / 100;
        calculatedBB = Math.max(10, calculatedBB);
        return ((calculatedBB + 5) / 10) * 10;
    }

    public static class PokerGameLogic {
        public enum HandRank {
            HIGH_CARD(1), PAIR(2), TWO_PAIR(3), THREE_OF_A_KIND(4), STRAIGHT(5), 
            FLUSH(6), FULL_HOUSE(7), FOUR_OF_A_KIND(8), STRAIGHT_FLUSH(9);
            public final int value;
            HandRank(int v) { value = v; }
        }
    
        public static class HandScore implements Comparable<HandScore> {
            public HandRank rank;
            public List<Integer> tieBreakers; 
            
            public HandScore(HandRank r, List<Integer> tb) {
                this.rank = r;
                this.tieBreakers = tb;
            }
    
            @Override
            public int compareTo(HandScore o) {
                if (this.rank.value != o.rank.value) return Integer.compare(this.rank.value, o.rank.value);
                for (int i = 0; i < this.tieBreakers.size() && i < o.tieBreakers.size(); i++) {
                    int cmp = Integer.compare(this.tieBreakers.get(i), o.tieBreakers.get(i));
                    if (cmp != 0) return cmp;
                }
                return 0;
            }
        }
    
        public static HandScore evaluate(List<Card> holeCards, List<Card> communityCards) {
            List<Card> all = new ArrayList<>(holeCards);
            all.addAll(communityCards);
            if (all.size() < 7) return new HandScore(HandRank.HIGH_CARD, new ArrayList<>());
            all.sort((o1, o2) -> Integer.compare(o2.rank().getValue(GameType.POKER), o1.rank().getValue(GameType.POKER)));
            return analyzeHand(all);
        }
        
        private static HandScore analyzeHand(List<Card> sevenCards) {
            int[] suitCounts = new int[4];
            int[] rankCounts = new int[15];
            int[][] suitRanks = new int[4][7];
            int[] suitRankCounts = new int[4];
            int[] sortedRanks = new int[7];
            
            for (int i = 0; i < 7; i++) {
                Card c = sevenCards.get(i);
                int suitIdx = c.suit().ordinal();
                int rankVal = c.rank().getValue(GameType.POKER);
                suitCounts[suitIdx]++;
                rankCounts[rankVal]++;
                suitRanks[suitIdx][suitRankCounts[suitIdx]] = rankVal;
                suitRankCounts[suitIdx]++;
                sortedRanks[i] = rankVal;
            }
            
            for (int i = 0; i < 6; i++) {
                for (int j = i + 1; j < 7; j++) {
                    if (sortedRanks[j] > sortedRanks[i]) {
                        int tmp = sortedRanks[i];
                        sortedRanks[i] = sortedRanks[j];
                        sortedRanks[j] = tmp;
                    }
                }
            }
            
            int flushSuit = -1;
            for (int s = 0; s < 4; s++) {
                if (suitCounts[s] >= 5) {
                    flushSuit = s;
                    break;
                }
            }
            
            boolean[] rankPresent = new boolean[15];
            for (int r : sortedRanks) {
                if (!rankPresent[r]) rankPresent[r] = true;
            }
            rankPresent[1] = rankPresent[14];
            
            int straightHigh = -1;
            int seq = 0;
            for (int r = 14; r >= 1; r--) {
                if (rankPresent[r]) {
                    seq++;
                    if (seq >= 5) straightHigh = r + 4;
                } else {
                    seq = 0;
                }
            }
            
            int fourRank = 0, threeRank = 0, pairRank = 0, secondPairRank = 0;
            boolean hasFour = false, hasThree = false, hasPair = false, hasSecondPair = false;
            
            for (int r = 14; r >= 2; r--) {
                int cnt = rankCounts[r];
                if (cnt == 4) { hasFour = true; fourRank = r; }
                else if (cnt == 3) {
                    if (!hasThree) { hasThree = true; threeRank = r; }
                    else if (!hasPair) { hasPair = true; pairRank = r; }
                }
                else if (cnt == 2) {
                    if (!hasPair) { hasPair = true; pairRank = r; }
                    else if (!hasSecondPair) { hasSecondPair = true; secondPairRank = r; }
                }
            }
            
            List<Integer> tie = new ArrayList<>();
            
            if (hasFour) {
                tie.add(fourRank);
                for (int r : sortedRanks) {
                    if (r != fourRank) { tie.add(r); break; }
                }
                return new HandScore(HandRank.FOUR_OF_A_KIND, tie);
            }
            
            if (hasThree && hasPair) {
                tie.add(threeRank);
                tie.add(pairRank);
                return new HandScore(HandRank.FULL_HOUSE, tie);
            }
            
            if (flushSuit >= 0) {
                int[] flushRankVals = suitRanks[flushSuit];
                int flushCount = suitRankCounts[flushSuit];
                for (int i = 0; i < flushCount - 1; i++) {
                    for (int j = i + 1; j < flushCount; j++) {
                        if (flushRankVals[j] > flushRankVals[i]) {
                            int tmp = flushRankVals[i];
                            flushRankVals[i] = flushRankVals[j];
                            flushRankVals[j] = tmp;
                        }
                    }
                }
                
                boolean[] flushRankPresent = new boolean[15];
                for (int i = 0; i < flushCount; i++) flushRankPresent[flushRankVals[i]] = true;
                flushRankPresent[1] = flushRankPresent[14];
                
                int flushSeq = 0;
                int flushStraightHigh = -1;
                for (int r = 14; r >= 1; r--) {
                    if (flushRankPresent[r]) {
                        flushSeq++;
                        if (flushSeq >= 5) flushStraightHigh = r + 4;
                    } else {
                        flushSeq = 0;
                    }
                }
                
                if (flushStraightHigh >= 0) {
                    tie.add(flushStraightHigh);
                    return new HandScore(HandRank.STRAIGHT_FLUSH, tie);
                }
                
                for (int i = 0; i < 5 && i < flushCount; i++) tie.add(flushRankVals[i]);
                return new HandScore(HandRank.FLUSH, tie);
            }
            
            if (straightHigh >= 0) {
                tie.add(straightHigh);
                return new HandScore(HandRank.STRAIGHT, tie);
            }
            
            if (hasThree) {
                tie.add(threeRank);
                int kickers = 0;
                for (int r : sortedRanks) {
                    if (r != threeRank) {
                        tie.add(r);
                        kickers++;
                        if (kickers == 2) break;
                    }
                }
                return new HandScore(HandRank.THREE_OF_A_KIND, tie);
            }
            
            if (hasPair && hasSecondPair) {
                tie.add(pairRank);
                tie.add(secondPairRank);
                for (int r : sortedRanks) {
                    if (r != pairRank && r != secondPairRank) {
                        tie.add(r);
                        break;
                    }
                }
                return new HandScore(HandRank.TWO_PAIR, tie);
            }
            
            if (hasPair) {
                tie.add(pairRank);
                int kickers = 0;
                for (int r : sortedRanks) {
                    if (r != pairRank) {
                        tie.add(r);
                        kickers++;
                        if (kickers == 3) break;
                    }
                }
                return new HandScore(HandRank.PAIR, tie);
            }
            
            for (int i = 0; i < 5; i++) tie.add(sortedRanks[i]);
            return new HandScore(HandRank.HIGH_CARD, tie);
        }
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

        state.round = Round.PREFLOP;
        // Heads-up poker: Small Blind (dealer) acts FIRST pre-flop
        // If player is dealer (SB), player acts first. If opponent is dealer (SB), opponent acts first.
        state.currentPlayer = getBigBlind() == Dealer.PLAYER ? CurrentPlayer.OPPONENT : CurrentPlayer.PLAYER;

        // Notify AI of new hand and position
        // AI is in position (acts last) when AI is the dealer
        ai.newHandStarted(state.dealer == Dealer.OPPONENT);

        // Reset betting round tracking for new hand
        ai.resetBettingRoundTracking(state.pot);

        evaluateHands();
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

    public void processPlayerAction(Action action, int raiseAmount) {
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
                // Pot award is handled by PokerHandler.determineWinner() to avoid double-awarding
                state.round = Round.SHOWDOWN;
                state.folder = CurrentPlayer.PLAYER; // Track that player folded
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
                // Defensive check: if opponent declared all-in, treat as CALL
                if (state.opponentDeclaredAllIn) {
                    int callAmt = state.opponentBet - state.playerBet;
                    state.playerStack -= callAmt;
                    state.playerBet = state.opponentBet;
                    state.displayPlayerBet = state.opponentBet;
                    state.pot += callAmt;
                    break;
                }
                state.opponentHasActed = false; // Opponent must respond to raise
                // raiseAmount is the desired total bet size (e.g., 3x opponent's raise)
                // NOT an additional amount on top of opponent's bet
                int totalBet = raiseAmount;
                int raiseAmountActual = totalBet - state.playerBet;
                // Protect against betting more than available stack
                if (raiseAmountActual > state.playerStack) {
                    raiseAmountActual = state.playerStack;
                    totalBet = state.playerBet + raiseAmountActual;
                }
                
                // Check if this is an all-in (player is betting their entire stack)
                if (raiseAmountActual >= state.playerStack) {
                    state.playerDeclaredAllIn = true;
                }
                
                // Check if player is raising more than opponent can possibly match (side pot)
                int playerContributionAfterRaise = totalBet;
                int currentOpponentContribution = state.opponentBet;
                if (playerContributionAfterRaise > currentOpponentContribution + state.opponentStack) {
                    int maxOpponentCanMatch = currentOpponentContribution + state.opponentStack;
                    int actualRaiseAmount = maxOpponentCanMatch - state.playerBet;
                    int excessToReturn = raiseAmountActual - actualRaiseAmount;
                    
                    // Player only risks what opponent can match
                    state.playerStack -= actualRaiseAmount;
                    // Only return excess if player hasn't declared all-in (all-in means all chips committed)
                    if (!state.playerDeclaredAllIn) {
                        state.playerStack += excessToReturn;
                    }
                    state.playerBet = maxOpponentCanMatch;
                    state.displayPlayerBet = maxOpponentCanMatch;
                    state.pot += actualRaiseAmount;
                } else {
                    state.playerStack -= raiseAmountActual;
                    state.playerBet = totalBet;
                    state.displayPlayerBet = totalBet;
                    state.pot += raiseAmountActual;
                }
                break;
case ALL_IN:
                state.playerDeclaredAllIn = true; // Flag that player has committed to all-in
                state.opponentHasActed = false; // Opponent must respond to all-in
                int allInAmount = state.playerStack;
                int opponentContributionAllIn = state.opponentBet;
                int playerContributionAfterAllIn = state.playerBet + allInAmount;

                // If player is putting in more than opponent can possibly match,
                // return the excess to player immediately (side pot logic)
                if (playerContributionAfterAllIn > opponentContributionAllIn + state.opponentStack) {
                    int maxOpponentCanMatch = opponentContributionAllIn + state.opponentStack;
                    int actualAllInAmount = maxOpponentCanMatch - state.playerBet;
                    int excessToReturn = allInAmount - actualAllInAmount;
                    
                    // Player only risks what opponent can match
                    state.pot += actualAllInAmount;
                    state.playerBet += actualAllInAmount;
                    state.displayPlayerBet = state.playerBet;
                    state.playerStack = excessToReturn; // Return excess chips
                } else {
                    // Normal all-in where opponent can match or has already matched
                    state.pot += allInAmount;
                    state.playerBet += allInAmount;
                    state.displayPlayerBet = state.playerBet;
                    state.playerStack = 0;
                }
                break;
        }

boolean isRaise = action == Action.RAISE || action == Action.ALL_IN;
        boolean isFold = action == Action.FOLD;
        boolean isCheck = action == Action.CHECK;
        boolean isPreFlop = state.round == Round.PREFLOP;
        boolean putMoneyInPot = action == Action.CALL || action == Action.RAISE || action == Action.ALL_IN;
        ai.trackPlayerAction(isRaise, isFold, isCheck, isPreFlop, putMoneyInPot);

        state.playerHasActed = true;

        if (state.round != Round.SHOWDOWN) {
            state.currentPlayer = CurrentPlayer.OPPONENT;
            checkRoundProgression();
        }
    }

    public PokerOpponentAI.AIResponse getOpponentAction() {
        int currentBetToCall = state.playerBet - state.opponentBet;
        // If player is all-in (declared all-in OR no chips left), AI can only call or fold, not raise
        if (state.playerDeclaredAllIn || state.playerStack <= 0) {
            return ai.decideAllInResponse(state.opponentHand, state.communityCards, currentBetToCall, state.pot);
        }
        return ai.decide(state.opponentHand, state.communityCards, currentBetToCall, state.pot, state.opponentStack);
    }

    public void processOpponentAction(PokerOpponentAI.AIResponse response) {
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
                // Pot award is handled by PokerHandler.determineWinner() to avoid double-awarding
                state.round = Round.SHOWDOWN;
                state.folder = CurrentPlayer.OPPONENT; // Track that opponent folded
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
                int raiseAmountActual = totalBet - state.opponentBet;
                // Protect against betting more than available stack
                if (raiseAmountActual > state.opponentStack) {
                    raiseAmountActual = state.opponentStack;
                    totalBet = state.opponentBet + raiseAmountActual;
                }
                
                // Check if opponent is going all-in (betting their entire stack)
                if (raiseAmountActual >= state.opponentStack) {
                    state.opponentDeclaredAllIn = true;
                }

                // Check if opponent is raising more than player can possibly match (side pot)
                int opponentContributionAfterRaise = totalBet;
                int currentPlayerContribution = state.playerBet;
                if (opponentContributionAfterRaise > currentPlayerContribution + state.playerStack) {
                    int maxPlayerCanMatch = currentPlayerContribution + state.playerStack;
                    int actualRaiseAmount = maxPlayerCanMatch - state.opponentBet;
                    int excessToReturn = raiseAmountActual - actualRaiseAmount;

                    // Opponent only risks what player can match
                    state.opponentStack -= actualRaiseAmount;
                    // Only return excess if opponent hasn't declared all-in (all-in means all chips committed)
                    if (!state.opponentDeclaredAllIn) {
                        state.opponentStack += excessToReturn;
                    }
                    state.opponentBet = maxPlayerCanMatch;
                    state.displayOpponentBet = maxPlayerCanMatch;
                    state.pot += actualRaiseAmount;
                    // Track committed chips for EV calculation (only what opponent can match)
                    ai.aiCommittedThisRound += actualRaiseAmount;
                } else {
                    state.opponentStack -= raiseAmountActual;
                    state.opponentBet = totalBet;
                    state.displayOpponentBet = totalBet;
                    state.pot += raiseAmountActual;
                    // Track committed chips for EV calculation
                    ai.aiCommittedThisRound += raiseAmountActual;
                }

                // If player has no chips left (all-in), they cannot respond to a raise
                // Treat this as both players having acted and progress to showdown
                if (state.playerStack <= 0) {
                    state.playerHasActed = true;
                } else {
                    state.playerHasActed = false; // Player must respond to raise
                }
                break;
}

        state.opponentHasActed = true;

        if (state.round != Round.SHOWDOWN) {
            state.currentPlayer = CurrentPlayer.PLAYER;
            checkRoundProgression();
        }
    }

private void checkRoundProgression() {
        if (state.playerBet == state.opponentBet && state.playerHasActed && state.opponentHasActed) {
            // Check if betting is closed (someone is all-in or declared all-in) - run out all remaining cards
            if (state.playerStack == 0 || state.opponentStack == 0 || 
                state.playerDeclaredAllIn || state.opponentDeclaredAllIn) {
                while (state.round != Round.SHOWDOWN) {
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
        
        // Note: All-in flags are already false when both players have chips
        // and neither declared all-in, so no reset needed

        // Reset AI's committed chips tracking when advancing to a new betting round
        ai.resetCommittedChips();

        // Reset betting round tracking for raise spiral prevention
        ai.resetBettingRoundTracking(state.pot);

        switch (state.round) {
            case PREFLOP:
                state.round = Round.FLOP;
                state.communityCards.add(deck.draw());
                state.communityCards.add(deck.draw());
                state.communityCards.add(deck.draw());
                break;
            case FLOP:
                state.round = Round.TURN;
                state.communityCards.add(deck.draw());
                break;
            case TURN:
                state.round = Round.RIVER;
                state.communityCards.add(deck.draw());
                break;
            case RIVER:
                state.round = Round.SHOWDOWN;
                break;
            case SHOWDOWN:
                return;
        }

        evaluateHands();

        if (state.round != Round.SHOWDOWN) {
            // Post-flop: Small Blind acts first (the dealer in heads-up)
            // If dealer is PLAYER, player acts first. If dealer is OPPONENT, opponent acts first.
            state.currentPlayer = state.dealer == Dealer.PLAYER ? CurrentPlayer.PLAYER : CurrentPlayer.OPPONENT;
        }
    }

    private void evaluateHands() {
        if (state.communityCards.size() >= 3) {
            PokerGameLogic.HandScore playerScore = PokerGameLogic.evaluate(state.playerHand, state.communityCards);
            PokerGameLogic.HandScore opponentScore = PokerGameLogic.evaluate(state.opponentHand, state.communityCards);
            state.playerHandRank = playerScore.rank;
            state.opponentHandRank = opponentScore.rank;
        } else {
            state.playerHandRank = null;
            state.opponentHandRank = null;
        }
    }
}
