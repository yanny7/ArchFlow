package app;

import java.util.List;

class Walker {
    private static final List<Handler> HANDLERS = List.of(
            insn -> Instructions.execute(insn),
            Reads::execute);
    private final Runnable task = () -> handle(1);

    Handler fallback() {
        return insn -> Reads.execute(-insn);
    }

    void handle(int insn) {
        for (Handler handler : HANDLERS) {
            if (handler.execute(insn)) {
                return;
            }
        }
    }
}
