package data.scripts.casino.Poker;

import data.scripts.casino.Strings;
import data.scripts.casino.PokerGame.PokerGameLogic.Rank;

public class CardUtils {
    public static final String getRankName(int rankValue) {
        return switch (rankValue)
        {
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

    public static final String getRankString(Rank rank) {
        return switch (rank) {
            case ACE -> "A";
            case KING -> "K";
            case QUEEN -> "Q";
            case JACK -> "J";
            case TEN -> "10";
            case NINE -> "9";
            case EIGHT -> "8";
            case SEVEN -> "7";
            case SIX -> "6";
            case FIVE -> "5";
            case FOUR -> "4";
            case THREE -> "3";
            case TWO -> "2";
        };
    }
}