package app;

interface Handler {
    boolean execute(int insn);

    default boolean accepts(int insn) {
        return insn >= 0;
    }
}
