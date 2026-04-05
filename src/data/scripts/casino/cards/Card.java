package data.scripts.casino.cards;

import data.scripts.casino.Strings;

public record Card(Rank rank, Suit suit, GameType type)
{

    public String displayName() {return suit.displayName;}

    

    public int value() {return rank.getValue(type);}

    public boolean isAce() {return rank == Rank.ACE;}

    @Override
    public String toString()
    {
        return "[" + Strings.format("poker_card_format.of", suit.displayName, rank.symbol) + "]";
    }
}
