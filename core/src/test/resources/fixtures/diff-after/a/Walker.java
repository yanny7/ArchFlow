package a;

class Walker {
    private final Context context = new Context();

    void walk(int depth) {
        if (depth > 0) {
            step(depth);
        }
    }

    void step(long depth) {

        b.Util.log((int) depth);
    }
}
