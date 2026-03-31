package data.scripts.casino.cards;

import data.scripts.casino.Strings;

public class Card {
    public final Rank rank;
    public final Suit suit;
    public final GameType type;

    public Card(Rank rank, Suit suit, GameType type) {
        this.rank = rank;
        this.suit = suit;
        this.type = type;
    }

    public String displayName() { return suit.displayName; }
    public String symbol() { return rank.symbol; }
    public int value() { return rank.getValue(type); }

    public boolean isAce() { return rank == Rank.ACE; }

    @Override
    public String toString() {
        return "[" + Strings.format("poker_card_format.of", suit.displayName, rank.symbol) + "]";
    }
}