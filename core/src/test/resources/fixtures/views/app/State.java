package app;

class State {
    private final java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<>();

    boolean pending() {
        return !stack.isEmpty();
    }

    boolean failed() {
        return false;
    }

    void push(int value) {
        stack.push(value);
        log(value);
    }

    void reset() {
        stack.clear();
        log(0);
        log(1);
        log(2);
        log(3);
        log(4);
    }

    private void log(int value) {
    }
}
