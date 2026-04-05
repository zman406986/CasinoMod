package data.scripts.casino.cards;

import java.util.ArrayList;
import java.util.Collections;

public class Deck {
    public final ArrayList<Card> cards = new ArrayList<>();
    public final int numDecks;
    public final GameType gameType;
    public final boolean autoRefresh;

    /**
     * POKER: one standard 52‑card deck, no auto‑refresh (return null when empty).
     * BLACKJACK: six standard decks shuffled together, auto‑refresh when empty.
     */
    public Deck(GameType gameType) {
        this.gameType = gameType;
        switch (gameType) {
        case POKER:
            this.numDecks = 1;
            this.autoRefresh = false;
            break;

        case BLACKJACK:
            this.numDecks = 6;
            this.autoRefresh = true;
            break;

        default:
            throw new IllegalArgumentException(gameType.toString());
        }
        init();
    }

    private void init() {
        for (int d = 0; d < numDecks; d++) {
            for (Suit s : Suit.values()) {
                for (Rank r : Rank.values()) {
                    cards.add(new Card(r, s, gameType));
                }
            }
        }
    }

    public final Card draw() {
        if (cards.isEmpty()) {
            if (autoRefresh) {
                final Deck fresh = new Deck(gameType);
                fresh.shuffle();
                cards.addAll(fresh.cards);
            } else {
                return null;
            }
        }
        return cards.remove(cards.size() - 1);
    }

    public final void shuffle() { Collections.shuffle(cards); }
}
