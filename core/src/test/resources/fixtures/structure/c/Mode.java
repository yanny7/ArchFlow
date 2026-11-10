package c;

public enum Mode {
    FAST,
    SLOW;

    Mode next() {
        return this == FAST ? SLOW : FAST;
    }
}
