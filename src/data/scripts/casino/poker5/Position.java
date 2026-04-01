package data.scripts.casino.poker5;

public enum Position {
    BUTTON,
    SMALL_BLIND,
    BIG_BLIND,
    UTG,
    CUT_OFF;

    public boolean isLatePosition() {
        return this == BUTTON || this == CUT_OFF;
    }

    public boolean isBlind() {
        return this == SMALL_BLIND || this == BIG_BLIND;
    }

    public boolean isInPositionVs(Position other) {
        return this.actOrderPostflop() > other.actOrderPostflop();
    }

    public int actOrderPreflop() {
        return switch (this) {
            case UTG -> 0;
            case CUT_OFF -> 1;
            case BUTTON -> 2;
            case SMALL_BLIND -> 3;
            case BIG_BLIND -> 4;
        };
    }

    public int actOrderPostflop() {
        return switch (this) {
            case SMALL_BLIND -> 0;
            case BIG_BLIND -> 1;
            case UTG -> 2;
            case CUT_OFF -> 3;
            case BUTTON -> 4;
        };
    }

    public static Position fromSeatIndex(int seatIndex, int buttonSeat) {
        int relativePos = (seatIndex - buttonSeat + 5) % 5;
        return switch (relativePos) {
            case 0 -> BUTTON;
            case 1 -> SMALL_BLIND;
            case 2 -> BIG_BLIND;
            case 3 -> UTG;
            case 4 -> CUT_OFF;
            default -> BUTTON;
        };
    }
}