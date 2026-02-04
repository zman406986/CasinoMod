package data.scripts.casino;

import java.util.*;

public class PokerGame {

    public enum Action { FOLD, CHECK, CALL, RAISE, ALL_IN }

    public enum Dealer { PLAYER, OPPONENT }

    public enum Round { PREFLOP, FLOP, TURN, RIVER, SHOWDOWN }

    public enum CurrentPlayer { PLAYER, OPPONENT }

    public static class PokerState {
        public List<PokerGameLogic.Card> playerHand;
        public List<PokerGameLogic.Card> opponentHand;
        public List<PokerGameLogic.Card> communityCards;
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
    }

    private PokerState state;
    private SimplePokerAI ai;
    private PokerGameLogic.Deck deck;
    private int startingStack;
    private int smallBlind;
    private int bigBlindAmount;

    public PokerGame() {
        this(1000, 1000, 10, 20);
    }

    public PokerGame(int playerStack, int opponentStack, int smallBlind, int bigBlind) {
        ai = new SimplePokerAI();
        state = new PokerState();
        this.startingStack = playerStack; // Keep for reference, though state.playerStack is initialized in startNewHand
        
        // Calculate BB proportional to stack size - 4x deeper than before for longer games
        // For smaller games: BB = stack / 80
        // For larger games: BB = stack / 200 (much deeper stack)
        int avgStack = (playerStack + opponentStack) / 2;
        int calculatedBB;
        if (avgStack <= 5000) {
            // Small game: BB = 1/80 of stack, round to nearest 10
            calculatedBB = Math.max(10, avgStack / 80);
            calculatedBB = (calculatedBB / 10) * 10; // Round down to nearest 10
            if (calculatedBB < 10) calculatedBB = 10;
        } else if (avgStack <= 20000) {
            // Medium game: BB = 1/120 of stack, round to nearest 10
            calculatedBB = Math.max(50, avgStack / 120);
            calculatedBB = (calculatedBB / 10) * 10; // Round down to nearest 10
            if (calculatedBB < 50) calculatedBB = 50;
        } else {
            // Large game: BB = 1/200 of stack, round to nearest 50
            calculatedBB = Math.max(100, avgStack / 200);
            calculatedBB = (calculatedBB / 50) * 50; // Round down to nearest 50
            if (calculatedBB < 100) calculatedBB = 100;
        }
        
        this.bigBlindAmount = calculatedBB;
        this.smallBlind = calculatedBB / 2;
        
        state.playerStack = playerStack;
        state.opponentStack = opponentStack;
        state.bigBlind = bigBlindAmount;
        
        startNewHand();
    }

    public static class PokerGameLogic {
        public enum Suit { SPADES, HEARTS, DIAMONDS, CLUBS }
        
        public enum Rank { 
            TWO(2, "2"), THREE(3, "3"), FOUR(4, "4"), FIVE(5, "5"), SIX(6, "6"), 
            SEVEN(7, "7"), EIGHT(8, "8"), NINE(9, "9"), TEN(10, "10"), 
            JACK(11, "J"), QUEEN(12, "Q"), KING(13, "K"), ACE(14, "A");
            
            public final int value;
            public final String symbol;
            Rank(int v, String s) { value = v; symbol = s; }
        }
        
        public enum HandRank {
            HIGH_CARD(1), PAIR(2), TWO_PAIR(3), THREE_OF_A_KIND(4), STRAIGHT(5), 
            FLUSH(6), FULL_HOUSE(7), FOUR_OF_A_KIND(8), STRAIGHT_FLUSH(9);
            public final int value;
            HandRank(int v) { value = v; }
        }
    
        public static class Card {
            public final Rank rank;
            public final Suit suit;
            public Card(Rank r, Suit s) { rank = r; suit = s; }
            @Override public String toString() {
                String s = switch(suit) {
                    case SPADES -> "Spades";
                    case HEARTS -> "Hearts";
                    case DIAMONDS -> "Diamonds";
                    case CLUBS -> "Clubs";
                };
                return "[" + rank.symbol + " of " + s + "]";
            }
        }
    
        public static class Deck {
            public final List<Card> cards = new ArrayList<>();
            public Deck() {
                for(Suit s : Suit.values()) for(Rank r : Rank.values()) cards.add(new Card(r, s));
            }
            public void shuffle() { Collections.shuffle(cards); }
            public Card draw() { return cards.isEmpty() ? null : cards.remove(cards.size()-1); }
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
            if (all.size() < 5) return new HandScore(HandRank.HIGH_CARD, new ArrayList<>());
            all.sort((o1, o2) -> Integer.compare(o2.rank.value, o1.rank.value));
            return analyzeHand(all);
        }
        
        private static HandScore analyzeHand(List<Card> sevenCards) {
            List<Card> flushCards = null;
            for(Suit s : Suit.values()) {
                 List<Card> suitCards = new ArrayList<>();
                 for(Card c : sevenCards) if(c.suit == s) suitCards.add(c);
                 if(suitCards.size() >= 5) { 
                     flushCards = suitCards; 
                     flushCards.sort((o1, o2) -> Integer.compare(o2.rank.value, o1.rank.value));
                     break; 
                 }
            }
            
            List<Integer> ranks = new ArrayList<>();
            for(Card c : sevenCards) if(!ranks.contains(c.rank.value)) ranks.add(c.rank.value);
            ranks.sort(Collections.reverseOrder());
            if (ranks.contains(14)) ranks.add(1); 
            
            List<Integer> straightHigh = null;
            int seq = 0;
            for(int i=0; i<ranks.size()-1; i++) {
                if (ranks.get(i) - ranks.get(i+1) == 1) {
                    seq++;
                    if (seq >= 4) { 
                        straightHigh = new ArrayList<>();
                        straightHigh.add(ranks.get(i-3));
                        break;
                    }
                } else {
                    seq = 0;
                }
            }
            
            int[] counts = new int[15];
            for(Card c : sevenCards) counts[c.rank.value]++;
            
            boolean four = false;
            boolean three = false;
            boolean pair = false;
            boolean secondPair = false;
            int fourRank = 0, threeRank = 0, pairRank = 0, secondPairRank = 0;
            
            for(int r=14; r>=2; r--) {
                if(counts[r] == 4) { four=true; fourRank=r; }
                else if(counts[r] == 3) { 
                    if(!three) { three=true; threeRank=r; }
                    else if(!pair) { pair=true; pairRank=r; } 
                }
                else if(counts[r] == 2) {
                    if(!pair) { pair=true; pairRank=r; }
                    else if(!secondPair) { secondPair=true; secondPairRank=r; }
                }
            }
            
            List<Integer> tie = new ArrayList<>();
            
            if(four) {
                tie.add(fourRank);
                for(Card c : sevenCards) if(c.rank.value != fourRank) { tie.add(c.rank.value); break; }
                return new HandScore(HandRank.FOUR_OF_A_KIND, tie);
            }
            else if(three && pair) { 
                 tie.add(threeRank);
                 tie.add(pairRank);
                 return new HandScore(HandRank.FULL_HOUSE, tie);
            }
             else if(flushCards != null) {
                 List<Integer> flushRanks = new ArrayList<>();
                 for(Card c : flushCards) flushRanks.add(c.rank.value);
                 if (flushRanks.contains(14)) flushRanks.add(1);
                 
                 int fseq = 0;
                 for(int i=0; i<flushRanks.size()-1; i++) {
                     if (flushRanks.get(i) - flushRanks.get(i+1) == 1) {
                         fseq++;
                         if (fseq >= 4) {
                             tie.add(flushRanks.get(i-3));
                             return new HandScore(HandRank.STRAIGHT_FLUSH, tie);
                         }
                     } else {
                         fseq = 0;
                     }
                 }

                 for(int i=0; i<5; i++) tie.add(flushCards.get(i).rank.value);
                 return new HandScore(HandRank.FLUSH, tie);
             }
            else if(straightHigh != null) {
                tie.add(straightHigh.get(0));
                return new HandScore(HandRank.STRAIGHT, tie);
            }
            else if(three) {
                tie.add(threeRank);
                int k=0;
                for(Card c: sevenCards) {
                    if(c.rank.value != threeRank) {
                        tie.add(c.rank.value);
                        k++;
                        if(k==2) break;
                    }
                }
                return new HandScore(HandRank.THREE_OF_A_KIND, tie);
            }
            else if(pair && secondPair) {
                tie.add(pairRank);
                tie.add(secondPairRank);
                for(Card c : sevenCards) {
                    if(c.rank.value != pairRank && c.rank.value != secondPairRank) {
                         tie.add(c.rank.value); break;
                    }
                }
                return new HandScore(HandRank.TWO_PAIR, tie);
            }
            else if(pair) {
                tie.add(pairRank);
                 int k=0;
                for(Card c : sevenCards) {
                    if(c.rank.value != pairRank) {
                         tie.add(c.rank.value); 
                         k++;
                         if(k==3) break;
                    }
                }
                return new HandScore(HandRank.PAIR, tie);
            }
            
            for(int i=0; i<5 && i<sevenCards.size(); i++) tie.add(sevenCards.get(i).rank.value);
            return new HandScore(HandRank.HIGH_CARD, tie);
        }
    }

    public static class SimplePokerAI {
        private final Random random = new Random();
        public enum Action { FOLD, CALL, RAISE, CHECK }
        public enum Personality { TIGHT, AGGRESSIVE, CALCULATED }
        public enum PlayerStyle { UNKNOWN, PASSIVE, BALANCED, AGGRESSIVE }
        
        public static class AIResponse {
            public Action action;
            public int raiseAmount; 
            public AIResponse(Action a, int amt) { action=a; raiseAmount=amt; }
        }
        
        // Monte Carlo result holder
        public static class MonteCarloResult {
            public int wins;
            public int ties;
            public int losses;
            public int samples;
            
            public MonteCarloResult(int wins, int ties, int losses, int samples) {
                this.wins = wins;
                this.ties = ties;
                this.losses = losses;
                this.samples = samples;
            }
            
            public float getWinProbability() { return (float) wins / samples; }
            public float getTieProbability() { return (float) ties / samples; }
            public float getLossProbability() { return (float) losses / samples; }
            public float getTotalEquity() { return (wins + ties * 0.5f) / samples; }
        }

        // Core AI state variables
        private float aggressionMeter = 0.5f;                    // 0.0 (passive) to 1.0 (aggressive)
        private final float[] aggressionHistory = new float[10];      // Circular buffer for smoothing
        private int historyIndex = 0;                            // Index for circular buffer
        private int playerStyle = 0;                             // 0: unknown, 1: passive, 2: balanced, 3: aggressive
        private int totalPlayerActions = 0;
        private int totalRaises = 0;
        private int totalCalls = 0;
        private int totalFolds = 0;
        private int handsPlayed = 0;
        private int vpipCount = 0;                               // Voluntarily Put $ In Pot
        private int pfrCount = 0;                                // Pre-Flop Raise count
        private Personality personality = Personality.CALCULATED;
        
        // Position tracking
        private boolean isInPosition = false;                    // Is AI acting after player (dealer position)
        
        // Anti-gullibility tracking
        private int playerBetsWithoutShowdown = 0;               // Times player bet and AI folded
        private int playerBetsTotal = 0;                         // Total player bets
        private int consecutiveBluffsCaught = 0;                 // Consecutive times AI folded to player bluff
        private int suspiciousModeHands = 0;                     // Remaining hands in suspicious mode
        private boolean isSuspicious = false;                    // Currently suspicious of player bluffs
        private int playerCheckRaises = 0;                       // Track check-raise frequency
        private int handsSinceLastShowdown = 0;                  // Hands without seeing player cards
        private int lastHandPot = 0;                             // Pot size of last hand
        private int timesBluffedByPlayer = 0;                    // Times player successfully bluffed AI

        public SimplePokerAI() {
            // Initialize with CALCULATED personality as default
            personality = Personality.CALCULATED;
            // Initialize aggression history array with Arrays.fill()
            Arrays.fill(aggressionHistory, 0.5f);
        }
        
        private void updatePersonality() {
            // Adapt AI personality based on detected player style
            PlayerStyle detectedStyle = PlayerStyle.values()[playerStyle];
            
            switch (detectedStyle) {
                case PASSIVE:
                    // Against passive players: play AGGRESSIVE to exploit them
                    personality = Personality.AGGRESSIVE;
                    break;
                case AGGRESSIVE:
                    // Against aggressive players: play TIGHT to trap them
                    personality = Personality.TIGHT;
                    break;
                case BALANCED:
                    // Against balanced players: play CALCULATED for optimal play
                    personality = Personality.CALCULATED;
                    break;
                case UNKNOWN:
                default:
                    // Unknown player: stick with CALCULATED
                    personality = Personality.CALCULATED;
                    break;
            }
        }
        
        private float getPersonalityThreshold(String thresholdType) {
            // Get threshold values based on current AI personality
            switch (personality) {
                case TIGHT:
                    switch (thresholdType) {
                        case "raise": return CasinoConfig.POKER_AI_TIGHT_THRESHOLD_RAISE / 100.0f;
                        case "fold": return CasinoConfig.POKER_AI_TIGHT_THRESHOLD_FOLD / 100.0f;
                        case "stackFold": return CasinoConfig.POKER_AI_STACK_PERCENT_FOLD_TIGHT;
                        default: return 0.5f;
                    }
                case AGGRESSIVE:
                    switch (thresholdType) {
                        case "raise": return CasinoConfig.POKER_AI_AGGRESSIVE_THRESHOLD_RAISE / 100.0f;
                        case "fold": return CasinoConfig.POKER_AI_AGGRESSIVE_THRESHOLD_FOLD / 100.0f;
                        case "stackFold": return 0.1f; // Aggressive AI rarely folds
                        default: return 0.5f;
                    }
                case CALCULATED:
                default:
                    switch (thresholdType) {
                        case "raise": return CasinoConfig.POKER_AI_NORMAL_THRESHOLD_RAISE / 100.0f;
                        case "fold": return CasinoConfig.POKER_AI_NORMAL_THRESHOLD_FOLD / 100.0f;
                        case "stackFold": return CasinoConfig.POKER_AI_STACK_PERCENT_CALL_LIMIT;
                        default: return 0.5f;
                    }
            }
        }
        
        private String estimatePlayerRange() {
            // Default range for unknown players
            String baseRange = "random";
            
            switch(PlayerStyle.values()[playerStyle]) {
                case PASSIVE:
                    return "tight_range";  // 25% of hands
                case AGGRESSIVE:
                    return "wide_range";   // 60% of hands
                case BALANCED:
                    return "standard_range";  // 40% of hands
                default:
                    return baseRange;  // Unknown player
            }
        }
        
        public String getCurrentPersonality() {
            // Return current AI personality for display/debugging
            return personality.name();
        }
        
        public String getPersonalityDescription() {
            // Return a human-readable description of current AI personality
            switch (personality) {
                case TIGHT:
                    return "The IPC Dealer is playing conservatively, waiting for premium hands.";
                case AGGRESSIVE:
                    return "The IPC Dealer is playing aggressively, applying pressure with frequent raises.";
                case CALCULATED:
                    return "The IPC Dealer is playing a balanced, calculated strategy.";
                default:
                    return "The IPC Dealer is adapting to your play style.";
            }
        }
        
        private int calculateBetSize(float equity, int potSize, int stackSize) {
            // Use configured raise amounts as base, with pot-based adjustments
            int[] raiseAmounts = CasinoConfig.POKER_RAISE_AMOUNTS;
            
            // Select base raise amount based on equity strength
            int baseRaise;
            if (equity > 0.65f) {
                // High strength - use largest raise or pot-based
                baseRaise = raiseAmounts[Math.min(raiseAmounts.length - 1, 3)];
                int potBasedBet = (int)(potSize * (0.6f + (random.nextFloat() * 0.2f - 0.1f)));
                baseRaise = Math.max(baseRaise, potBasedBet);
            } else if (equity > 0.45f) {
                // Medium strength - use middle raise
                baseRaise = raiseAmounts[Math.min(raiseAmounts.length - 1, 2)];
                int potBasedBet = (int)(potSize * (0.45f + (random.nextFloat() * 0.1f - 0.05f)));
                baseRaise = Math.max(baseRaise, potBasedBet);
            } else {
                // Bluff sizing - use smallest raise
                baseRaise = raiseAmounts[0];
                int potBasedBet = (int)(potSize * (0.35f + (random.nextFloat() * 0.1f - 0.05f)));
                baseRaise = Math.max(baseRaise, potBasedBet);
            }
            
            // Add random variation if configured
            if (CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION > 0) {
                baseRaise += random.nextInt(CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION);
            }
            
            return Math.min(baseRaise, stackSize); // Don't bet more than we have
        }
        
        private AIResponse randomDeviation(float equity, float potOdds, int stackSize, int potSize) {
            int deviationType = random.nextInt(4) + 1; // 1-4
            int bluffRaise = 0;
            int overbet = 0;
            
            switch(deviationType) {
                case 1: // Hero call
                    if (equity > potOdds * 0.8f) {
                        return new AIResponse(Action.CALL, 0);
                    }
                    break;
                case 2: // Bluff raise
                    if (equity < 0.4f && random.nextFloat() < 0.3f) {
                        bluffRaise = (int)(potSize * 0.5f);
                        bluffRaise = Math.min(bluffRaise, stackSize);
                        return new AIResponse(Action.RAISE, bluffRaise);
                    }
                    break;
                case 3: // Check with strong hand
                    if (equity > 0.7f) {
                        return new AIResponse(Action.CALL, 0); // Slow play - just call
                    }
                    break;
                case 4: // Overbet
                    if (equity > 0.75f) {
                        overbet = (int)(potSize * 1.5f);
                        overbet = Math.min(overbet, stackSize);
                        return new AIResponse(Action.RAISE, overbet);
                    }
                    break;
            }
            
            // Default decision if no condition met
            if (equity > potOdds) {
                return new AIResponse(Action.CALL, 0);
            } else {
                return new AIResponse(Action.FOLD, 0);
            }
        }
        
        public AIResponse decide(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards, 
                                int currentBetToCall, int potSize, int stackSize, int opponentStackSize) {
            // Removed unused opponentStackSize parameter
            // Determine if this is pre-flop or post-flop
            if (communityCards.isEmpty()) {
                return preFlopDecision(holeCards, currentBetToCall, potSize, stackSize);
            } else {
                return postFlopDecision(holeCards, communityCards, currentBetToCall, potSize, stackSize);
            }
        }
        
        private AIResponse preFlopDecision(List<PokerGameLogic.Card> holeCards, int currentBetToCall, int potSize, int stackSize) {
            // Update AI personality based on current player style
            updatePersonality();

            // Calculate pre-flop equity
            float equity = calculatePreflopEquity(holeCards);

            // Calculate big blinds for stack size assessment
            int bigBlind = Math.max(1, potSize / 3); // Approximate BB from pot
            float bbStack = (float) stackSize / bigBlind;

            // Position adjustment: play more hands when in position (BB acts last preflop)
            float positionBonus = isInPosition ? 0.05f : 0f;
            equity += positionBonus;

            // Short stack push/fold strategy (< 15 BB)
            if (bbStack < 15 && currentBetToCall > 0) {
                return shortStackDecision(equity, currentBetToCall, potSize, stackSize, bbStack);
            }

            // If player didn't raise (checked or limped), always continue
            if (currentBetToCall == 0) {
                // Player checked or limped - play based on hand strength + position
                float openThreshold = isInPosition ? 0.35f : 0.45f; // Looser when in position

                if (equity >= openThreshold || (equity >= 0.30f && random.nextFloat() < 0.3f)) {
                    // Raise with strong hands or sometimes as a bluff
                    int raiseAmount = Math.min(stackSize / 20, stackSize);
                    raiseAmount = Math.max(bigBlind * 3, raiseAmount);
                    raiseAmount = Math.max(CasinoConfig.POKER_AI_MIN_RAISE_VALUE, raiseAmount);
                    return new AIResponse(Action.RAISE, raiseAmount);
                } else {
                    return new AIResponse(Action.CALL, 0); // Call to see flop
                }
            } else {
                // Player bet/raised - use EV-based decision making
                float potOdds = (float)currentBetToCall / (potSize + currentBetToCall);

                // SPECIAL RULE: Always call to BB (currentBetToCall == bigBlind) to not deny the game
                // This happens when player raises from SB (limps/raises to BB amount)
                if (currentBetToCall == bigBlind) {
                    // Always complete the BB to not deny the player the game
                    // But still consider raising with strong hands
                    if (equity > 0.60f && random.nextFloat() < 0.4f) {
                        // 3-bet with strong hands sometimes
                        int threeBetSize = bigBlind * 3;
                        threeBetSize = Math.min(threeBetSize, stackSize);
                        return new AIResponse(Action.RAISE, Math.max(threeBetSize, CasinoConfig.POKER_AI_MIN_RAISE_VALUE));
                    }
                    return new AIResponse(Action.CALL, 0);
                }

                // Calculate EV of calling
                float evCall = calculateCallEV(equity, potSize, currentBetToCall);
                float evFold = 0f;

                // Calculate 3-bet sizing and EV
                int threeBetSize = currentBetToCall * 3; // 3x the raise
                threeBetSize = Math.max(threeBetSize, bigBlind * 9); // At least 9x BB
                threeBetSize = Math.min(threeBetSize, stackSize - currentBetToCall);

                // Estimate fold probability based on player style
                String playerRange = estimatePlayerRange();
                float foldProb = estimateFoldProbability(playerRange, potSize + currentBetToCall, threeBetSize);
                float evRaise = calculateRaiseEV(equity, potSize, currentBetToCall, threeBetSize, foldProb);

                // For bluff 3-bets: if equity is marginal, treat as bluff
                if (equity < 0.55f) {
                    float bluffEV = calculateBluffEV(foldProb, potSize, threeBetSize);
                    evRaise = Math.max(evRaise, bluffEV);
                }

                // Apply frequency cap: only 3-bet ~30% of the time when facing a raise
                // This prevents the re-raise spiral
                boolean should3Bet = false;
                if (equity > 0.65f) {
                    // Premium hands: 3-bet for value 60% of the time
                    should3Bet = random.nextFloat() < 0.60f;
                } else if (equity > 0.55f) {
                    // Strong hands: 3-bet 30% of the time
                    should3Bet = random.nextFloat() < 0.30f;
                } else if (equity > 0.45f && foldProb > 0.40f) {
                    // Bluff 3-bet: only with good fold equity, 20% frequency
                    should3Bet = random.nextFloat() < 0.20f;
                }

                // Only 3-bet if EV favors it AND frequency cap allows
                if (should3Bet && evRaise > evCall && evRaise > 0) {
                    threeBetSize = Math.max(threeBetSize, CasinoConfig.POKER_AI_MIN_RAISE_VALUE);
                    if (CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION > 0) {
                        threeBetSize += random.nextInt(CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION);
                    }
                    return new AIResponse(Action.RAISE, threeBetSize);
                }

                // CRITICAL: Don't call crazy all-ins with bad EV
                // If the bet is large relative to pot (> 3x pot) and equity is marginal, fold
                float betToPotRatio = (float) currentBetToCall / Math.max(1, potSize);
                if (betToPotRatio > 3.0f && equity < 0.55f) {
                    // Large bet, weak hand - fold unless we have amazing pot odds
                    if (potOdds > equity * 0.8f) {
                        return new AIResponse(Action.CALL, 0); // Call if pot odds are decent
                    }
                    return new AIResponse(Action.FOLD, 0);
                }

                // Don't call all-in shoves with less than 45% equity unless pot odds are great
                if (currentBetToCall >= stackSize * 0.8f && equity < 0.45f) {
                    if (potOdds > equity * 0.9f) {
                        return new AIResponse(Action.CALL, 0);
                    }
                    return new AIResponse(Action.FOLD, 0);
                }

                // Otherwise decide between call and fold based on EV
                float callThreshold = getPersonalityThreshold("fold");
                if (evCall > evFold && equity > callThreshold) {
                    return new AIResponse(Action.CALL, 0);
                } else if (equity < callThreshold * 0.5f) {
                    return new AIResponse(Action.FOLD, 0);
                }

                // Default: call with marginal hands (heads-up adjustment)
                return new AIResponse(Action.CALL, 0);
            }
        }
        
        private AIResponse shortStackDecision(float equity, int currentBetToCall, int potSize, int stackSize, float bbStack) {
            // Push/fold strategy for short stacks
            float potOdds = (float)currentBetToCall / (potSize + currentBetToCall);
            
            // Very short stack (< 10 BB): push with decent hands
            if (bbStack < 10) {
                if (equity > 0.50f || (equity > 0.40f && random.nextFloat() < 0.7f)) {
                    // All-in
                    return new AIResponse(Action.RAISE, stackSize);
                } else if (potOdds < 0.25f && equity > 0.35f) {
                    // Call with good pot odds
                    return new AIResponse(Action.CALL, 0);
                } else {
                    return new AIResponse(Action.FOLD, 0);
                }
            }
            
            // Short stack (10-15 BB): slightly more selective
            if (equity > 0.55f || (equity > 0.45f && random.nextFloat() < 0.6f)) {
                return new AIResponse(Action.RAISE, Math.min(stackSize, currentBetToCall * 3));
            } else if (equity > 0.40f) {
                return new AIResponse(Action.CALL, 0);
            } else {
                return new AIResponse(Action.FOLD, 0);
            }
        }
        
        private AIResponse postFlopDecision(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards, 
                                          int currentBetToCall, int potSize, int stackSize) {
            // Estimate player range based on history and aggression
            String playerRange = estimatePlayerRange();
            
            // Run Monte Carlo simulation for detailed equity
            MonteCarloResult mcResult = runMonteCarloSimulation(holeCards, communityCards, playerRange, 500);
            float equity = mcResult.getTotalEquity();
            
            // Calculate implied odds adjustment for draws
            float impliedOddsBonus = calculateImpliedOddsBonus(holeCards, communityCards, equity);
            equity += impliedOddsBonus;
            
            // Use EV-based decision making
            return postFlopEVDecision(mcResult, currentBetToCall, potSize, stackSize, playerRange);
        }
        
        private float calculateImpliedOddsBonus(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards, float currentEquity) {
            // Add equity for drawing hands based on outs
            int outs = countOuts(holeCards, communityCards);
            int streetsRemaining = 5 - communityCards.size();
            
            // Each out is worth ~2% per street
            float drawEquity = outs * 0.02f * streetsRemaining;
            
            // Only add if we have a reasonable draw but not made hand
            if (currentEquity < 0.60f && drawEquity > 0.05f) {
                return Math.min(drawEquity * 0.5f, 0.15f); // Cap at 15% bonus
            }
            return 0f;
        }
        
        private int countOuts(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards) {
            int outs = 0;
            
            // Check for flush draw
            Map<PokerGameLogic.Suit, Integer> suitCounts = new HashMap<>();
            for (PokerGameLogic.Card c : holeCards) {
                suitCounts.put(c.suit, suitCounts.getOrDefault(c.suit, 0) + 1);
            }
            for (PokerGameLogic.Card c : communityCards) {
                suitCounts.put(c.suit, suitCounts.getOrDefault(c.suit, 0) + 1);
            }
            
            for (int count : suitCounts.values()) {
                if (count == 4) {
                    outs += 9; // Flush draw
                }
            }
            
            // Check for straight draw (simplified)
            Set<Integer> ranks = new HashSet<>();
            for (PokerGameLogic.Card c : holeCards) ranks.add(c.rank.value);
            for (PokerGameLogic.Card c : communityCards) ranks.add(c.rank.value);
            if (ranks.contains(14)) ranks.add(1); // Ace low
            
            List<Integer> sorted = new ArrayList<>(ranks);
            Collections.sort(sorted);
            
            int maxSeq = 1;
            int currentSeq = 1;
            for (int i = 0; i < sorted.size() - 1; i++) {
                if (sorted.get(i+1) - sorted.get(i) == 1) {
                    currentSeq++;
                    maxSeq = Math.max(maxSeq, currentSeq);
                } else {
                    currentSeq = 1;
                }
            }
            
            if (maxSeq == 4) {
                outs += 8; // Open-ended straight draw
            } else if (maxSeq == 3 && sorted.size() >= 3) {
                outs += 4; // Gutshot
            }
            
            return outs;
        }
        
        private AIResponse postFlopEVDecision(MonteCarloResult mcResult, int currentBetToCall, int potSize, int stackSize, String playerRange) {
            updatePersonality();
            
            float equity = mcResult.getTotalEquity();
            float winProb = mcResult.getWinProbability();
            
            // Calculate EV for each action
            float evFold = 0f;
            float evCall = calculateCallEV(equity, potSize, currentBetToCall);
            
            // Calculate raise sizes to consider
            int[] raiseSizes = {
                potSize / 2,  // Half pot
                potSize,      // Pot size
                potSize * 2   // 2x pot
            };
            
            float bestRaiseEV = Float.NEGATIVE_INFINITY;
            int bestRaiseSize = 0;
            
            for (int raiseSize : raiseSizes) {
                if (raiseSize > stackSize) continue;
                
                float foldProb = estimateFoldProbability(playerRange, potSize, raiseSize);
                float raiseEV = calculateRaiseEV(equity, potSize, currentBetToCall, raiseSize, foldProb);
                
                // Adjust for bluffing: if equity is low, this is a bluff
                if (equity < 0.45f) {
                    float bluffEV = calculateBluffEV(foldProb, potSize, raiseSize);
                    raiseEV = Math.max(raiseEV, bluffEV);
                }
                
                if (raiseEV > bestRaiseEV) {
                    bestRaiseEV = raiseEV;
                    bestRaiseSize = raiseSize;
                }
            }
            
            // Apply personality adjustments to EV
            evFold = adjustEVForPersonality(evFold, Action.FOLD);
            evCall = adjustEVForPersonality(evCall, Action.CALL);
            bestRaiseEV = adjustEVForPersonality(bestRaiseEV, Action.RAISE);
            
            // Anti-gullibility: adjust if suspicious of player bluffs
            if (shouldBeSuspicious()) {
                evFold -= 0.1f * potSize; // Reduce fold EV (make calling more attractive)
                evCall += 0.05f * potSize; // Increase call EV
            }
            
            // Anti-gullibility: stubborn caller - don't fold to small bets
            if (shouldMakeStubbornCall(equity, currentBetToCall, potSize)) {
                evFold = Float.NEGATIVE_INFINITY; // Never fold
                evCall += 0.1f * potSize; // Boost call EV
            }
            
            // Select best action based on EV
            if (bestRaiseEV > evCall && bestRaiseEV > evFold && bestRaiseSize > 0) {
                // Ensure minimum raise
                bestRaiseSize = Math.max(bestRaiseSize, CasinoConfig.POKER_AI_MIN_RAISE_VALUE);
                bestRaiseSize = Math.min(bestRaiseSize, stackSize);
                return new AIResponse(Action.RAISE, bestRaiseSize);
            } else if (evCall > evFold) {
                return new AIResponse(Action.CALL, 0);
            } else {
                return new AIResponse(Action.FOLD, 0);
            }
        }
        
        private float adjustEVForPersonality(float ev, Action action) {
            switch (personality) {
                case TIGHT:
                    switch (action) {
                        case FOLD:
                            return ev * 1.2f; // Tight AI prefers folding
                        case CALL:
                            return ev * 0.95f;
                        case RAISE:
                            return ev * 0.85f; // Tight AI bluffs less
                        default:
                            return ev;
                    }
                case AGGRESSIVE:
                    switch (action) {
                        case FOLD:
                            return ev * 0.7f; // Aggressive AI hates folding
                        case CALL:
                            return ev * 1.05f;
                        case RAISE:
                            return ev * 1.15f; // Aggressive AI prefers raising
                        default:
                            return ev;
                    }
                case CALCULATED:
                default:
                    return ev; // No adjustment for calculated
            }
        }
        
        // Legacy method kept for compatibility but now uses EV-based logic
        private AIResponse postFlopDecisionLogic(float equity, float potOdds, int stackSize, int potSize) {
            // This method is now a wrapper that creates a mock MonteCarloResult
            MonteCarloResult mockResult = new MonteCarloResult(
                (int)(equity * 100), 
                0, 
                (int)((1 - equity) * 100), 
                100
            );
            return postFlopEVDecision(mockResult, 0, potSize, stackSize, "standard_range");
        }
        
        // Static cache for preflop equities (169 unique starting hands)
        private static final Map<String, Float> preflopEquityCache = new HashMap<>();
        private static boolean preflopCacheInitialized = false;
        
        private void initializePreflopEquityCache() {
            if (preflopCacheInitialized) return;
            
            // Generate all 169 unique starting hand combinations
            PokerGameLogic.Deck deck = new PokerGameLogic.Deck();
            for (int i = 0; i < deck.cards.size(); i++) {
                for (int j = i + 1; j < deck.cards.size(); j++) {
                    PokerGameLogic.Card c1 = deck.cards.get(i);
                    PokerGameLogic.Card c2 = deck.cards.get(j);
                    
                    // Create canonical key (sorted by rank, suitedness)
                    String key = createHandKey(c1, c2);
                    
                    if (!preflopEquityCache.containsKey(key)) {
                        float equity = calculatePreflopEquityMonteCarlo(c1, c2);
                        preflopEquityCache.put(key, equity);
                    }
                }
            }
            
            preflopCacheInitialized = true;
        }
        
        private String createHandKey(PokerGameLogic.Card c1, PokerGameLogic.Card c2) {
            // Normalize: higher rank first
            int v1 = Math.max(c1.rank.value, c2.rank.value);
            int v2 = Math.min(c1.rank.value, c2.rank.value);
            boolean suited = (c1.suit == c2.suit);
            
            // Key format: "rank1_rank2_suited" or "rank1_rank2_offsuit"
            // For pairs: just "rank_pair"
            if (v1 == v2) {
                return v1 + "_pair";
            }
            return v1 + "_" + v2 + "_" + (suited ? "s" : "o");
        }
        
        private float calculatePreflopEquityMonteCarlo(PokerGameLogic.Card c1, PokerGameLogic.Card c2) {
            // Run quick Monte Carlo for preflop equity (50 samples is enough for preflop)
            List<PokerGameLogic.Card> ourHand = new ArrayList<>();
            ourHand.add(c1);
            ourHand.add(c2);
            
            int wins = 0;
            int samples = 50;
            
            for (int i = 0; i < samples; i++) {
                PokerGameLogic.Deck deck = new PokerGameLogic.Deck();
                deck.cards.remove(c1);
                deck.cards.remove(c2);
                Collections.shuffle(deck.cards, random);
                
                // Draw opponent hand
                List<PokerGameLogic.Card> oppHand = new ArrayList<>();
                oppHand.add(deck.cards.remove(0));
                oppHand.add(deck.cards.remove(0));
                
                // Draw 5 community cards
                List<PokerGameLogic.Card> community = new ArrayList<>();
                for (int j = 0; j < 5; j++) {
                    community.add(deck.cards.remove(0));
                }
                
                PokerGameLogic.HandScore ourScore = PokerGameLogic.evaluate(ourHand, community);
                PokerGameLogic.HandScore oppScore = PokerGameLogic.evaluate(oppHand, community);
                
                int cmp = ourScore.compareTo(oppScore);
                if (cmp > 0) wins++;
                else if (cmp == 0) wins += 0.5f;
            }
            
            return (float) wins / samples;
        }
        
        private float calculatePreflopEquity(List<PokerGameLogic.Card> holeCards) {
            // Initialize cache on first use
            initializePreflopEquityCache();
            
            // Lookup from cache
            PokerGameLogic.Card c1 = holeCards.get(0);
            PokerGameLogic.Card c2 = holeCards.get(1);
            String key = createHandKey(c1, c2);
            
            Float cached = preflopEquityCache.get(key);
            if (cached != null) {
                return cached;
            }
            
            // Fallback to simple calculation (shouldn't happen)
            return calculatePreflopEquitySimple(holeCards);
        }
        
        private float calculatePreflopEquitySimple(List<PokerGameLogic.Card> holeCards) {
            // Simplified pre-flop equity calculation based on hand strength
            PokerGameLogic.Card c1 = holeCards.get(0);
            PokerGameLogic.Card c2 = holeCards.get(1);
            int v1 = Math.max(c1.rank.value, c2.rank.value);
            int v2 = Math.min(c1.rank.value, c2.rank.value);
            
            float equity = 0.35f; // Default value
            
            // Premium pairs
            if (v1 == v2) {
                if (v1 >= 11) equity = 0.80f; // J, Q, K, A
                else if (v1 >= 8) equity = 0.70f; // Strong pairs
                else equity = 0.55f; // Small pairs
            }
            // Suited connectors
            else if (c1.suit == c2.suit && Math.abs(v1 - v2) == 1) {
                equity = 0.65f; // Suited connectors
            }
            // Other suited
            else if (c1.suit == c2.suit) {
                if (v1 == 14) equity = 0.60f; // Suited ace
                else if (v1 >= 11) equity = 0.55f; // Suited face cards
                else equity = 0.45f; // Other suited
            }
            // Offsuit connectors
            else if (Math.abs(v1 - v2) == 1) {
                equity = 0.50f; // Connectors
            }
            // High cards
            else if (v1 >= 11) {
                equity = 0.50f; // At least one face card
            }
            
            return equity;
        }
        
        private MonteCarloResult runMonteCarloSimulation(
                List<PokerGameLogic.Card> holeCards, 
                List<PokerGameLogic.Card> communityCards,
                String playerRange,
                int samples) {
            int wins = 0;
            int ties = 0;
            int losses = 0;
            
            for (int i = 0; i < samples; i++) {
                // Create temporary deck without known cards
                PokerGameLogic.Deck tempDeck = new PokerGameLogic.Deck();
                tempDeck.cards.removeAll(holeCards);
                tempDeck.cards.removeAll(communityCards);
                
                // Shuffle and complete the board
                Collections.shuffle(tempDeck.cards, random);
                List<PokerGameLogic.Card> completeBoard = new ArrayList<>(communityCards);
                
                // Add remaining cards to complete board
                int cardsNeeded = 5 - communityCards.size();
                for (int j = 0; j < cardsNeeded && !tempDeck.cards.isEmpty(); j++) {
                    completeBoard.add(tempDeck.cards.remove(0));
                }
                
                // Generate opponent hand from range
                List<PokerGameLogic.Card> opponentHand = generateRandomOpponentHand(playerRange, tempDeck);
                
                // Evaluate hands
                PokerGameLogic.HandScore ourScore = PokerGameLogic.evaluate(holeCards, completeBoard);
                PokerGameLogic.HandScore oppScore = PokerGameLogic.evaluate(opponentHand, completeBoard);
                
                int cmp = ourScore.compareTo(oppScore);
                if (cmp > 0) {
                    wins++;
                } else if (cmp == 0) {
                    ties++;
                } else {
                    losses++;
                }
                
                // Early exit for extreme equities after 50 samples
                if (i == 49) {
                    float currentEquity = (wins + ties * 0.5f) / (i + 1);
                    if (currentEquity > 0.90f || currentEquity < 0.10f) {
                        // Equity is extreme, no need for more samples
                        return new MonteCarloResult(wins, ties, losses, i + 1);
                    }
                }
            }
            
            return new MonteCarloResult(wins, ties, losses, samples);
        }
        
        private float calculateEquityMonteCarlo(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards, String playerRange) {
            // Use 500 samples for better accuracy
            MonteCarloResult result = runMonteCarloSimulation(holeCards, communityCards, playerRange, 500);
            return result.getTotalEquity();
        }
        
        // EV Calculation methods
        private float calculateCallEV(float equity, int potSize, int betToCall) {
            // EV of calling: equity * (pot + betToCall) - (1 - equity) * betToCall
            float winAmount = potSize + betToCall;
            float lossAmount = betToCall;
            return equity * winAmount - (1 - equity) * lossAmount;
        }
        
        private float calculateRaiseEV(float equity, int potSize, int currentBet, int raiseAmount, float foldProbability) {
            // EV of raising = (foldProbability * pot) + (1 - foldProbability) * [equity * (pot + 2*raise) - (1-equity) * raise]
            float evWhenCalled = equity * (potSize + 2 * raiseAmount) - (1 - equity) * raiseAmount;
            return foldProbability * potSize + (1 - foldProbability) * evWhenCalled;
        }
        
        private float calculateBluffEV(float foldProbability, int potSize, int bluffAmount) {
            // EV of bluffing = (foldProbability * pot) - (1 - foldProbability) * bluffAmount
            return foldProbability * potSize - (1 - foldProbability) * bluffAmount;
        }
        
        private float estimateFoldProbability(String playerRange, int potSize, int betSize) {
            // Estimate how often player will fold based on their style and pot odds
            float baseFoldProb = 0.3f; // Default 30% fold rate
            
            // Adjust based on player style
            switch (playerRange) {
                case "tight_range":
                    baseFoldProb = 0.45f; // Tight players fold more
                    break;
                case "wide_range":
                    baseFoldProb = 0.20f; // Loose players fold less
                    break;
                case "standard_range":
                default:
                    baseFoldProb = 0.30f;
                    break;
            }
            
            // Adjust based on pot odds (bigger bet = more folds)
            float potOdds = (float) betSize / (potSize + betSize);
            if (potOdds > 0.5f) {
                baseFoldProb += 0.15f; // Large bet, more folds
            } else if (potOdds < 0.25f) {
                baseFoldProb -= 0.10f; // Small bet, fewer folds
            }
            
            return Math.min(0.8f, Math.max(0.1f, baseFoldProb));
        }
        
        private List<PokerGameLogic.Card> generateRandomOpponentHand(String playerRange, PokerGameLogic.Deck deck) {
            // Generate a hand based on the player's estimated range
            List<PokerGameLogic.Card> hand = new ArrayList<>();
            
            // Define hand categories based on poker hand rankings
            List<List<PokerGameLogic.Card>> premiumHands = new ArrayList<>(); // AA, KK, QQ, AK
            List<List<PokerGameLogic.Card>> strongHands = new ArrayList<>();  // JJ, TT, AQ, AJ, KQ
            List<List<PokerGameLogic.Card>> playableHands = new ArrayList<>(); // 99-22, AT, KJ, QJ, suited connectors
            List<List<PokerGameLogic.Card>> weakHands = new ArrayList<>();     // Everything else
            
            // Generate all possible hands and categorize them
            PokerGameLogic.Deck tempDeck = new PokerGameLogic.Deck();
            for (int i = 0; i < tempDeck.cards.size(); i++) {
                for (int j = i + 1; j < tempDeck.cards.size(); j++) {
                    List<PokerGameLogic.Card> possibleHand = new ArrayList<>();
                    possibleHand.add(tempDeck.cards.get(i));
                    possibleHand.add(tempDeck.cards.get(j));
                    
                    float equity = calculatePreflopEquity(possibleHand);
                    
                    if (equity >= 0.70f) {
                        premiumHands.add(possibleHand);
                    } else if (equity >= 0.55f) {
                        strongHands.add(possibleHand);
                    } else if (equity >= 0.40f) {
                        playableHands.add(possibleHand);
                    } else {
                        weakHands.add(possibleHand);
                    }
                }
            }
            
            // Select hand based on player range
            List<List<PokerGameLogic.Card>> selectedPool;
            switch (playerRange) {
                case "tight_range":
                    // 70% premium, 25% strong, 5% playable
                    float tightRoll = random.nextFloat();
                    if (tightRoll < 0.70f && !premiumHands.isEmpty()) {
                        selectedPool = premiumHands;
                    } else if (tightRoll < 0.95f && !strongHands.isEmpty()) {
                        selectedPool = strongHands;
                    } else if (!playableHands.isEmpty()) {
                        selectedPool = playableHands;
                    } else {
                        selectedPool = weakHands;
                    }
                    break;
                case "wide_range":
                    // 20% premium, 30% strong, 30% playable, 20% weak
                    float wideRoll = random.nextFloat();
                    if (wideRoll < 0.20f && !premiumHands.isEmpty()) {
                        selectedPool = premiumHands;
                    } else if (wideRoll < 0.50f && !strongHands.isEmpty()) {
                        selectedPool = strongHands;
                    } else if (wideRoll < 0.80f && !playableHands.isEmpty()) {
                        selectedPool = playableHands;
                    } else {
                        selectedPool = weakHands;
                    }
                    break;
                case "standard_range":
                default:
                    // 35% premium, 35% strong, 25% playable, 5% weak
                    float stdRoll = random.nextFloat();
                    if (stdRoll < 0.35f && !premiumHands.isEmpty()) {
                        selectedPool = premiumHands;
                    } else if (stdRoll < 0.70f && !strongHands.isEmpty()) {
                        selectedPool = strongHands;
                    } else if (stdRoll < 0.95f && !playableHands.isEmpty()) {
                        selectedPool = playableHands;
                    } else {
                        selectedPool = weakHands;
                    }
                    break;
            }
            
            // Try to find a hand from the selected pool that doesn't conflict with known cards
            if (!selectedPool.isEmpty()) {
                // Shuffle the pool for randomness
                Collections.shuffle(selectedPool, random);
                
                for (List<PokerGameLogic.Card> candidateHand : selectedPool) {
                    // Check if both cards are still available in the deck
                    boolean available = true;
                    for (PokerGameLogic.Card c : candidateHand) {
                        if (!deck.cards.contains(c)) {
                            available = false;
                            break;
                        }
                    }
                    
                    if (available) {
                        hand.addAll(candidateHand);
                        deck.cards.removeAll(candidateHand);
                        return hand;
                    }
                }
            }
            
            // Fallback: draw random cards from deck
            if (hand.isEmpty() && deck.cards.size() >= 2) {
                hand.add(deck.cards.remove(0));
                hand.add(deck.cards.remove(0));
            }
            
            return hand;
        }
        
        // Removed unused method: calculateEquity(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards, int stackSize, int opponentStackSize)
        
        private float estimatePreFlopEquity(List<PokerGameLogic.Card> holeCards) {
            PokerGameLogic.Card c1 = holeCards.get(0);
            PokerGameLogic.Card c2 = holeCards.get(1);
            int v1 = Math.max(c1.rank.value, c2.rank.value);
            int v2 = Math.min(c1.rank.value, c2.rank.value);
            
            float equity = 35; // Default value instead of redundant initialization
            
            // Premium pairs
            if (v1 == v2) {
                if (v1 >= 11) equity = 80; // J, Q, K, A
                else if (v1 >= 8) equity = 70; // Strong pairs
                else equity = 55; // Small pairs
            }
            // Suited connectors
            else if (c1.suit == c2.suit && Math.abs(v1 - v2) == 1) {
                equity = 65; // Suited connectors
            }
            // Other suited
            else if (c1.suit == c2.suit) {
                if (v1 == 14) equity = 60; // Suited ace
                else if (v1 >= 11) equity = 55; // Suited face cards
                else equity = 45; // Other suited
            }
            // Offsuit connectors
            else if (Math.abs(v1 - v2) == 1) {
                equity = 50; // Connectors
            }
            // High cards
            else if (v1 >= 11) {
                equity = 50; // At least one face card
            }
            // Weak hands are covered by default value
            
            return equity;
        }
    


        private float getStrengthPercentage(PokerGameLogic.HandScore hand) {
            float base = switch(hand.rank) {
                case HIGH_CARD -> 10;
                case PAIR -> 35;
                case TWO_PAIR -> 55;
                case THREE_OF_A_KIND -> 70;
                case STRAIGHT -> 85;
                case FLUSH -> 90;
                case FULL_HOUSE -> 95;
                case FOUR_OF_A_KIND -> 99;
                case STRAIGHT_FLUSH -> 100;
            };
            if (!hand.tieBreakers.isEmpty()) {
                base += (hand.tieBreakers.get(0) / 14f) * 5f;
            }
            return Math.min(100f, base);
        }

        private boolean hasFlushDraw(List<PokerGameLogic.Card> hole, List<PokerGameLogic.Card> comm) {
            Map<PokerGameLogic.Suit, Integer> counts = new HashMap<>();
            // Enhanced for loop instead of traditional for loop
            for (PokerGameLogic.Card c : hole) counts.put(c.suit, counts.getOrDefault(c.suit, 0) + 1);
            for (PokerGameLogic.Card c : comm) counts.put(c.suit, counts.getOrDefault(c.suit, 0) + 1);
            for (int val : counts.values()) if (val >= 4) return true;
            return false;
        }

        private boolean hasStraightDraw(List<PokerGameLogic.Card> hole, List<PokerGameLogic.Card> comm) {
            Set<Integer> ranks = new HashSet<>();
            for (PokerGameLogic.Card c : hole) ranks.add(c.rank.value);
            for (PokerGameLogic.Card c : comm) ranks.add(c.rank.value);
            if (ranks.contains(14)) ranks.add(1); 

            List<Integer> sorted = new ArrayList<>(ranks);
            Collections.sort(sorted);
            
            int maxSeq = 0;
            int currentSeq = 1;
            for (int i = 0; i < sorted.size() - 1; i++) {
                if (sorted.get(i+1) - sorted.get(i) == 1) {
                    currentSeq++;
                    maxSeq = Math.max(maxSeq, currentSeq);
                } else {
                    currentSeq = 1;
                }
            }
            return maxSeq >= 4; 
        }
        
        public void trackPlayerAction(boolean isRaise, boolean isFold, boolean isCheck, boolean isPreFlop, boolean putMoneyInPot) {
            totalPlayerActions++;
            
            if (isRaise) {
                totalRaises++;
                if (isPreFlop) pfrCount++;
                playerBetsTotal++;
            } else if (isFold) {
                totalFolds++;
            } else if (!isCheck) {
                // Must be a call
                totalCalls++;
                playerBetsTotal++;
            }
            
            // Track check-raises
            if (isRaise && !isPreFlop && !putMoneyInPot) {
                playerCheckRaises++;
            }
            
            // Track VPIP (Voluntarily Put $ In Pot) - excludes checking
            if (putMoneyInPot) {
                vpipCount++;
            }
            
            // Update aggression meter based on player actions
            updateAggressionMeter(isRaise);
            
            // Classify player style using VPIP/PFR metrics (more accurate)
            if (handsPlayed > 3 && totalPlayerActions > 5) {
                float vpip = (float) vpipCount / handsPlayed;
                float pfr = (float) pfrCount / handsPlayed;
                float af = totalCalls > 0 ? (float) totalRaises / totalCalls : totalRaises;
                
                // Classification based on poker statistics
                if (vpip < 0.25f && pfr < 0.15f) {
                    playerStyle = 1; // PASSIVE (tight)
                } else if (vpip > 0.40f && af > 1.5f) {
                    playerStyle = 3; // AGGRESSIVE (loose-aggressive)
                } else {
                    playerStyle = 2; // BALANCED
                }
            }
        }
        
        // Anti-gullibility: track when AI folds to player bet
        public void trackAIFoldedToPlayerBet(int potSize) {
            playerBetsWithoutShowdown++;
            consecutiveBluffsCaught++;
            lastHandPot = potSize;
            handsSinceLastShowdown++;
            
            // Enter suspicious mode if player is bluffing too often
            float bluffRate = (float) consecutiveBluffsCaught / Math.max(1, handsSinceLastShowdown);
            if (consecutiveBluffsCaught >= 2 || bluffRate > 0.40f) {
                isSuspicious = true;
                suspiciousModeHands = 2 + random.nextInt(2); // 2-3 hands of suspicion
            }
        }
        
        // Anti-gullibility: track when player shows down a hand
        public void trackPlayerShowdown(boolean playerWasBluffing) {
            handsSinceLastShowdown = 0;
            consecutiveBluffsCaught = 0;
            
            if (playerWasBluffing) {
                timesBluffedByPlayer++;
                // After being bluffed, become more suspicious
                if (timesBluffedByPlayer >= 2) {
                    isSuspicious = true;
                    suspiciousModeHands = 3;
                }
            }
        }
        
        // Anti-gullibility: check if AI should be suspicious of player bluffs
        private boolean shouldBeSuspicious() {
            if (suspiciousModeHands > 0) {
                return true;
            }
            
            // Check if player has high bluff success rate
            if (playerBetsTotal > 5) {
                float bluffSuccessRate = (float) playerBetsWithoutShowdown / playerBetsTotal;
                if (bluffSuccessRate > 0.50f) {
                    return true; // Player is folding too often, they might be bluffing
                }
            }
            
            // Check if player check-raises frequently (trap indicator)
            if (handsPlayed > 3 && playerCheckRaises > handsPlayed / 3) {
                return true; // Player check-raises too much, be careful
            }
            
            return false;
        }
        
        // Anti-gullibility: make stubborn call with weak hand
        private boolean shouldMakeStubbornCall(float equity, int betToCall, int potSize) {
            // Never fold to small bets with any pair or A-high
            if (betToCall < potSize / 5 && equity > 0.25f) {
                return true;
            }
            
            // If suspicious, call wider
            if (isSuspicious && equity > 0.20f) {
                return random.nextFloat() < 0.6f; // 60% chance to call when suspicious
            }
            
            return false;
        }
        
        public void newHandStarted(boolean aiIsDealer) {
            handsPlayed++;
            // In heads-up poker:
            // - Pre-flop: Dealer (SB) acts FIRST, BB acts last (in position)
            // - Post-flop: Dealer (SB) acts FIRST (out of position), BB acts last (in position)
            // So AI is "in position" (acts last) when AI is NOT the dealer (i.e., AI is BB)
            isInPosition = !aiIsDealer;
            
            // Decrement suspicious mode counter
            if (suspiciousModeHands > 0) {
                suspiciousModeHands--;
                if (suspiciousModeHands == 0) {
                    isSuspicious = false;
                }
            }
            
            handsSinceLastShowdown++;
        }
        
        private void updateAggressionMeter(boolean isRaise) {
            // Track last 10 actions with exponential decay
            float recentAggression = 0;
            float weight = 1.0f;
            
            // Add the current action to the history
            float currentActionWeight = isRaise ? 1.0f : 0.3f; // Raises are more aggressive than calls
            aggressionHistory[historyIndex] = currentActionWeight;
            historyIndex = (historyIndex + 1) % aggressionHistory.length; // Move to next index
            
            // Calculate weighted average of recent actions
            for (int i = 0; i < aggressionHistory.length; i++) {
                recentAggression += weight * aggressionHistory[i];
                weight *= 0.8f; // Decay older actions
            }
            
            // Normalize by the sum of weights
            float totalWeight = 0;
            weight = 1.0f;
            for (int i = 0; i < aggressionHistory.length; i++) {
                totalWeight += weight;
                weight *= 0.8f;
            }
            
            if (totalWeight > 0) {
                recentAggression /= totalWeight;
            }
            
            // Update meter with smoothing
            aggressionMeter = 0.7f * aggressionMeter + 0.3f * recentAggression;
        }
    }

    public PokerState getState() {
        return state;
    }

    public SimplePokerAI getAI() {
        return ai;
    }

    public String getAIPersonality() {
        return ai.getCurrentPersonality();
    }

    public String getAIPersonalityDescription() {
        return ai.getPersonalityDescription();
    }

    public void startNewHand() {
        deck = new PokerGameLogic.Deck();
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
        state.pot = 0;

        state.dealer = state.dealer == Dealer.PLAYER ? Dealer.OPPONENT : Dealer.PLAYER;

        postBlinds();

        state.round = Round.PREFLOP;
        // Pre-flop: First to act is the player AFTER the Big Blind (UTG - Under the Gun)
        // If player is BB, opponent acts first. If opponent is BB, player acts first.
        state.currentPlayer = getBigBlind() == Dealer.PLAYER ? CurrentPlayer.OPPONENT : CurrentPlayer.PLAYER;

        // Notify AI of new hand and position
        // AI is in position (acts last) when AI is the dealer
        ai.newHandStarted(state.dealer == Dealer.OPPONENT);

        evaluateHands();
    }

    /**
     * Returns who has the Big Blind position for the current hand
     */
    public Dealer getBigBlind() {
        // Big Blind is the player who is NOT the dealer
        return state.dealer == Dealer.PLAYER ? Dealer.OPPONENT : Dealer.PLAYER;
    }

    /**
     * Returns who has the Small Blind position for the current hand
     */
    public Dealer getSmallBlind() {
        // Small Blind is the dealer
        return state.dealer;
    }

    /**
     * Returns true if it's post-flop (flop, turn, or river)
     */
    public boolean isPostFlop() {
        return state.round == Round.FLOP || state.round == Round.TURN || state.round == Round.RIVER;
    }

    private void postBlinds() {
        if (state.dealer == Dealer.PLAYER) {
            // Small blind (player) - ensure doesn't go negative
            int playerSmallBlind = Math.min(smallBlind, state.playerStack);
            state.playerBet = playerSmallBlind;
            state.playerStack -= playerSmallBlind;
            
            // Big blind (opponent) - ensure doesn't go negative
            int opponentBigBlind = Math.min(bigBlindAmount, state.opponentStack);
            state.opponentBet = opponentBigBlind;
            state.opponentStack -= opponentBigBlind;
        } else {
            // Small blind (opponent) - ensure doesn't go negative
            int opponentSmallBlind = Math.min(smallBlind, state.opponentStack);
            state.opponentBet = opponentSmallBlind;
            state.opponentStack -= opponentSmallBlind;
            
            // Big blind (player) - ensure doesn't go negative
            int playerBigBlind = Math.min(bigBlindAmount, state.playerStack);
            state.playerBet = playerBigBlind;
            state.playerStack -= playerBigBlind;
        }
        state.pot = state.playerBet + state.opponentBet;
    }

    public void processPlayerAction(Action action, int raiseAmount) {
        switch (action) {
            case FOLD:
                state.opponentStack += state.pot;
                state.pot = 0;
                state.round = Round.SHOWDOWN;
                break;
            case CHECK:
                break;
            case CALL:
                int callAmount = state.opponentBet - state.playerBet;
                state.playerStack -= callAmount;
                state.playerBet = state.opponentBet;
                state.pot += callAmount;
                break;
            case RAISE:
                int totalBet = state.opponentBet + raiseAmount;
                int raiseAmountActual = totalBet - state.playerBet;
                state.playerStack -= raiseAmountActual;
                state.playerBet = totalBet;
                state.pot += raiseAmountActual;
                break;
            case ALL_IN:
                int allInAmount = state.playerStack;
                state.pot += allInAmount;
                state.playerBet += allInAmount;
                state.playerStack = 0;
                break;
        }

        boolean isRaise = action == Action.RAISE || action == Action.ALL_IN;
        boolean isFold = action == Action.FOLD;
        boolean isCheck = action == Action.CHECK;
        boolean isPreFlop = state.round == Round.PREFLOP;
        boolean putMoneyInPot = action == Action.CALL || action == Action.RAISE || action == Action.ALL_IN;
        ai.trackPlayerAction(isRaise, isFold, isCheck, isPreFlop, putMoneyInPot);

        if (state.round != Round.SHOWDOWN) {
            state.currentPlayer = CurrentPlayer.OPPONENT;
            checkRoundProgression();
        }
    }

    public SimplePokerAI.AIResponse getOpponentAction() {
        int currentBetToCall = state.playerBet - state.opponentBet;
        return ai.decide(state.opponentHand, state.communityCards, currentBetToCall, state.pot, state.opponentStack, state.playerStack);
    }

    public void processOpponentAction(SimplePokerAI.AIResponse response) {
        switch (response.action) {
            case FOLD:
                state.playerStack += state.pot;
                state.pot = 0;
                state.round = Round.SHOWDOWN;
                break;
            case CHECK:
                break;
            case CALL:
                int callAmount = state.playerBet - state.opponentBet;
                state.opponentStack -= callAmount;
                state.opponentBet = state.playerBet;
                state.pot += callAmount;
                break;
            case RAISE:
                int raiseAmount = response.raiseAmount;
                int totalBet = state.playerBet + raiseAmount;
                int raiseAmountActual = totalBet - state.opponentBet;
                state.opponentStack -= raiseAmountActual;
                state.opponentBet = totalBet;
                state.pot += raiseAmountActual;
                break;
        }

        if (state.round != Round.SHOWDOWN) {
            state.currentPlayer = CurrentPlayer.PLAYER;
            checkRoundProgression();
        }
    }

    private void checkRoundProgression() {
        if (state.playerBet == state.opponentBet) {
            advanceRound();
        }
    }

    private void advanceRound() {
        state.playerBet = 0;
        state.opponentBet = 0;

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