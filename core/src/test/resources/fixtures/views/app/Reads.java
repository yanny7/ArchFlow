package app;

class Reads implements Handler {
    @Override
    public boolean execute(State state) {
        if (state.pending()) {
            state.push(1);
            state.reset();
            return true;
        }
        return false;
    }
}
