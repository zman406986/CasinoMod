package data.scripts.casino.cards;

import data.scripts.casino.Strings;

public enum Suit {
    SPADES("poker_suits.spades"),
    HEARTS("poker_suits.hearts"),
    DIAMONDS("poker_suits.diamonds"),
    CLUBS("poker_suits.clubs");

    public final String displayName;

    Suit(String key) {
        this.displayName = Strings.get(key);
    }
}