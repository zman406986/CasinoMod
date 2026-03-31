package data.scripts.casino.cards;

public enum Rank {
    TWO("2"), THREE("3"), FOUR("4"), FIVE("5"), SIX("6"),
    SEVEN("7"), EIGHT("8"), NINE("9"), TEN("10"),
    JACK("J"), QUEEN("Q"), KING("K"), ACE("A");

    public final String symbol;

    Rank(String symbol) {
        this.symbol = symbol;
    }

    public final int getValue(GameType game) {
        switch (game) {
        case POKER:
            return getPokerValue();
        case BLACKJACK:
            return getBlackjackValue();
        default:
            throw new IllegalArgumentException(game.toString());
        }
    }

    private final int getPokerValue() {
        return switch (this) {
            case TWO -> 2; case THREE -> 3; case FOUR -> 4; case FIVE -> 5;
            case SIX -> 6; case SEVEN -> 7; case EIGHT -> 8; case NINE -> 9;
            case TEN -> 10; case JACK -> 11; case QUEEN -> 12; case KING -> 13;
            case ACE -> 14;
        };
    }

    private final int getBlackjackValue() {
        return switch (this) {
            case TWO -> 2; case THREE -> 3; case FOUR -> 4; case FIVE -> 5;
            case SIX -> 6; case SEVEN -> 7; case EIGHT -> 8; case NINE -> 9;
            case TEN, JACK, QUEEN, KING -> 10;
            case ACE -> 11;
        };
    }
}