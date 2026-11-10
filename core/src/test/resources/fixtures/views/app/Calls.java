package app;

class Calls implements Handler {
    @Override
    public boolean execute(State state) {
        state.push(0);
        return false;
    }
}
