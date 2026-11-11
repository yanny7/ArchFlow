package app;

import java.util.List;

class Walker {
    private final List<Handler> handlers;

    Walker(List<Handler> handlers) {
        this.handlers = handlers;
    }

    void walk(State state) {
        while (state.pending()) {
            step(state);
        }
        if (state.failed()) {
            state.toString();
            undeclared.run();
        }
        state.reset();
    }

    private void step(State state) {
        for (Handler handler : handlers) {
            if (handler.execute(state)) {
                return;
            }
        }
    }
}
