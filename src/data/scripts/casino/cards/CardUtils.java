package data.scripts.casino.cards;

import data.scripts.casino.Strings;

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
}