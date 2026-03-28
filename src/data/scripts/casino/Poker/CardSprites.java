package data.scripts.casino.Poker;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

import data.scripts.casino.PokerGame.PokerGameLogic.Rank;
import data.scripts.casino.PokerGame.PokerGameLogic.Suit;

/**
 * All the sprites are static, which necessitates resizing, repositioning and recoloring before each render call.
 */
public class CardSprites {
    private static final SettingsAPI settings = Global.getSettings();
    public static final SpriteAPI BACK_AQUA = settings.getSprite("card", "back_aqua");
    public static final SpriteAPI BACK_BLACK = settings.getSprite("card", "back_black");
    public static final SpriteAPI BACK_BLUE = settings.getSprite("card", "back_blue");
    public static final SpriteAPI BACK_FUCHSIA = settings.getSprite("card", "back_fuchsia");
    public static final SpriteAPI BACK_GRAY = settings.getSprite("card", "back_gray");
    public static final SpriteAPI BACK_GREEN = settings.getSprite("card", "back_green");
    public static final SpriteAPI BACK_LIME = settings.getSprite("card", "back_lime");
    public static final SpriteAPI BACK_MAROON = settings.getSprite("card", "back_maroon");
    public static final SpriteAPI BACK_NAVY = settings.getSprite("card", "back_navy");
    public static final SpriteAPI BACK_OLIVE = settings.getSprite("card", "back_olive");
    public static final SpriteAPI BACK = settings.getSprite("card", "back");
    public static final SpriteAPI BACK_PURPLE = settings.getSprite("card", "back_purple");
    public static final SpriteAPI BACK_RED = settings.getSprite("card", "back_red");
    public static final SpriteAPI BACK_SILVER = settings.getSprite("card", "back_silver");
    public static final SpriteAPI BACK_TEAL = settings.getSprite("card", "back_teal");
    public static final SpriteAPI BACK_YELLOW = settings.getSprite("card", "back_yellow");
    public static final SpriteAPI CARD_BASE = settings.getSprite("card", "card_base");
    public static final SpriteAPI CLUB_1 = settings.getSprite("card", "club_1");
    public static final SpriteAPI CLUB_2 = settings.getSprite("card", "club_2");
    public static final SpriteAPI CLUB_3 = settings.getSprite("card", "club_3");
    public static final SpriteAPI CLUB_4 = settings.getSprite("card", "club_4");
    public static final SpriteAPI CLUB_5 = settings.getSprite("card", "club_5");
    public static final SpriteAPI CLUB_6 = settings.getSprite("card", "club_6");
    public static final SpriteAPI CLUB_7 = settings.getSprite("card", "club_7");
    public static final SpriteAPI CLUB_8 = settings.getSprite("card", "club_8");
    public static final SpriteAPI CLUB_9 = settings.getSprite("card", "club_9");
    public static final SpriteAPI CLUB_10 = settings.getSprite("card", "club_10");
    public static final SpriteAPI CLUB_JACK = settings.getSprite("card", "club_jack");
    public static final SpriteAPI CLUB_QUEEN = settings.getSprite("card", "club_queen");
    public static final SpriteAPI CLUB_KING = settings.getSprite("card", "club_king");
    public static final SpriteAPI DIAMOND_1 = settings.getSprite("card", "diamond_1");
    public static final SpriteAPI DIAMOND_2 = settings.getSprite("card", "diamond_2");
    public static final SpriteAPI DIAMOND_3 = settings.getSprite("card", "diamond_3");
    public static final SpriteAPI DIAMOND_4 = settings.getSprite("card", "diamond_4");
    public static final SpriteAPI DIAMOND_5 = settings.getSprite("card", "diamond_5");
    public static final SpriteAPI DIAMOND_6 = settings.getSprite("card", "diamond_6");
    public static final SpriteAPI DIAMOND_7 = settings.getSprite("card", "diamond_7");
    public static final SpriteAPI DIAMOND_8 = settings.getSprite("card", "diamond_8");
    public static final SpriteAPI DIAMOND_9 = settings.getSprite("card", "diamond_9");
    public static final SpriteAPI DIAMOND_10 = settings.getSprite("card", "diamond_10");
    public static final SpriteAPI DIAMOND_JACK = settings.getSprite("card", "diamond_jack");
    public static final SpriteAPI DIAMOND_QUEEN = settings.getSprite("card", "diamond_queen");
    public static final SpriteAPI DIAMOND_KING = settings.getSprite("card", "diamond_king");
    public static final SpriteAPI HEART_1 = settings.getSprite("card", "heart_1");
    public static final SpriteAPI HEART_2 = settings.getSprite("card", "heart_2");
    public static final SpriteAPI HEART_3 = settings.getSprite("card", "heart_3");
    public static final SpriteAPI HEART_4 = settings.getSprite("card", "heart_4");
    public static final SpriteAPI HEART_5 = settings.getSprite("card", "heart_5");
    public static final SpriteAPI HEART_6 = settings.getSprite("card", "heart_6");
    public static final SpriteAPI HEART_7 = settings.getSprite("card", "heart_7");
    public static final SpriteAPI HEART_8 = settings.getSprite("card", "heart_8");
    public static final SpriteAPI HEART_9 = settings.getSprite("card", "heart_9");
    public static final SpriteAPI HEART_10 = settings.getSprite("card", "heart_10");
    public static final SpriteAPI HEART_JACK = settings.getSprite("card", "heart_jack");
    public static final SpriteAPI HEART_QUEEN = settings.getSprite("card", "heart_queen");
    public static final SpriteAPI HEART_KING = settings.getSprite("card", "heart_king");
    public static final SpriteAPI SPADE_1 = settings.getSprite("card", "spade_1");
    public static final SpriteAPI SPADE_2 = settings.getSprite("card", "spade_2");
    public static final SpriteAPI SPADE_3 = settings.getSprite("card", "spade_3");
    public static final SpriteAPI SPADE_4 = settings.getSprite("card", "spade_4");
    public static final SpriteAPI SPADE_5 = settings.getSprite("card", "spade_5");
    public static final SpriteAPI SPADE_6 = settings.getSprite("card", "spade_6");
    public static final SpriteAPI SPADE_7 = settings.getSprite("card", "spade_7");
    public static final SpriteAPI SPADE_8 = settings.getSprite("card", "spade_8");
    public static final SpriteAPI SPADE_9 = settings.getSprite("card", "spade_9");
    public static final SpriteAPI SPADE_10 = settings.getSprite("card", "spade_10");
    public static final SpriteAPI SPADE_JACK = settings.getSprite("card", "spade_jack");
    public static final SpriteAPI SPADE_QUEEN = settings.getSprite("card", "spade_queen");
    public static final SpriteAPI SPADE_KING = settings.getSprite("card", "spade_king");
    public static final SpriteAPI JOKER_BLACK = settings.getSprite("card", "joker_black");
    public static final SpriteAPI JOKER_RED = settings.getSprite("card", "joker_red");
    public static final SpriteAPI SUIT_CLUB = settings.getSprite("card", "suit_club");
    public static final SpriteAPI SUIT_DIAMOND = settings.getSprite("card", "suit_diamond");
    public static final SpriteAPI SUIT_HEART = settings.getSprite("card", "suit_heart");
    public static final SpriteAPI SUIT_SPADE = settings.getSprite("card", "suit_spade");

    public static final SpriteAPI get(Suit suit, Rank rank) {
        switch (suit) {
        case SPADES:
            switch (rank) {
                case ACE:   return SPADE_1;
                case TWO:   return SPADE_2;
                case THREE: return SPADE_3;
                case FOUR:  return SPADE_4;
                case FIVE:  return SPADE_5;
                case SIX:   return SPADE_6;
                case SEVEN: return SPADE_7;
                case EIGHT: return SPADE_8;
                case NINE:  return SPADE_9;
                case TEN:   return SPADE_10;
                case JACK:  return SPADE_JACK;
                case QUEEN: return SPADE_QUEEN;
                case KING:  return SPADE_KING;
                default:    return null;
            }
        case HEARTS:
            switch (rank) {
                case ACE:   return HEART_1;
                case TWO:   return HEART_2;
                case THREE: return HEART_3;
                case FOUR:  return HEART_4;
                case FIVE:  return HEART_5;
                case SIX:   return HEART_6;
                case SEVEN: return HEART_7;
                case EIGHT: return HEART_8;
                case NINE:  return HEART_9;
                case TEN:   return HEART_10;
                case JACK:  return HEART_JACK;
                case QUEEN: return HEART_QUEEN;
                case KING:  return HEART_KING;
                default:    return null;
            }
        case DIAMONDS:
            switch (rank) {
                case ACE:   return DIAMOND_1;
                case TWO:   return DIAMOND_2;
                case THREE: return DIAMOND_3;
                case FOUR:  return DIAMOND_4;
                case FIVE:  return DIAMOND_5;
                case SIX:   return DIAMOND_6;
                case SEVEN: return DIAMOND_7;
                case EIGHT: return DIAMOND_8;
                case NINE:  return DIAMOND_9;
                case TEN:   return DIAMOND_10;
                case JACK:  return DIAMOND_JACK;
                case QUEEN: return DIAMOND_QUEEN;
                case KING:  return DIAMOND_KING;
                default:    return null;
            }
        case CLUBS:
            switch (rank) {
                case ACE:   return CLUB_1;
                case TWO:   return CLUB_2;
                case THREE: return CLUB_3;
                case FOUR:  return CLUB_4;
                case FIVE:  return CLUB_5;
                case SIX:   return CLUB_6;
                case SEVEN: return CLUB_7;
                case EIGHT: return CLUB_8;
                case NINE:  return CLUB_9;
                case TEN:   return CLUB_10;
                case JACK:  return CLUB_JACK;
                case QUEEN: return CLUB_QUEEN;
                case KING:  return CLUB_KING;
                default:    return null;
            }
        default: return null;
        }
    }
}