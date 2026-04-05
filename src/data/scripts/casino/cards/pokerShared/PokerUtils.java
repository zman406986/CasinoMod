package data.scripts.casino.cards.pokerShared;

import java.util.List;

import data.scripts.casino.Strings;
import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.Deck;
import data.scripts.casino.cards.GameType;
import data.scripts.casino.cards.Rank;
import data.scripts.casino.cards.Suit;

public class PokerUtils {

    private static final Rank[] RANK_BY_VALUE = new Rank[15];
    
    static {
        for (Rank r : Rank.values()) {
            RANK_BY_VALUE[r.getValue(GameType.POKER)] = r;
        }
    }

    public static String cardToString(Card card) {
        if (card == null) return "";
        return card.value() + "-" + card.suit().name();
    }

    public static Card stringToCard(String str) {
        if (str == null || str.isEmpty()) return null;
        String[] parts = str.split("-");
        if (parts.length != 2) return null;
        try {
            int rankValue = Integer.parseInt(parts[0]);
            Suit suit = Suit.valueOf(parts[1]);
            if (rankValue >= 2 && rankValue <= 14) {
                return new Card(RANK_BY_VALUE[rankValue], suit, GameType.POKER);
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    public static int calculateBigBlind(int avgStack) {
        int calculatedBB = avgStack / 100;
        calculatedBB = Math.max(10, calculatedBB);
        return ((calculatedBB + 5) / 10) * 10;
    }

    public static void dealCommunityCards(PokerRound fromRound, Deck deck, List<Card> communityCards) {
        switch (fromRound) {
            case PREFLOP -> {
                communityCards.add(deck.draw());
                communityCards.add(deck.draw());
                communityCards.add(deck.draw());
            }
            case FLOP, TURN -> communityCards.add(deck.draw());
            case RIVER, SHOWDOWN -> { }
        }
    }

    public static String getRankName(int rankValue) {
        return switch (rankValue) {
            case 14 -> Strings.get("poker_ranks.ace");
            case 13 -> Strings.get("poker_ranks.king");
            case 12 -> Strings.get("poker_ranks.queen");
            case 11 -> Strings.get("poker_ranks.jack");
            case 10 -> "10";
            case 9 -> "9";
            case 8 -> "8";
            case 7 -> "7";
            case 6 -> "6";
            case 5 -> "5";
            case 4 -> "4";
            case 3 -> "3";
            case 2 -> "2";
            default -> String.valueOf(rankValue);
        };
    }
}
