package a;

class Walker {
    private final Context context = new Context();

    void walk(int depth) {
        if (depth > 0) {
            step(depth);
        }
    }

    void step(int depth) {

        b.Util.log(depth);
    }
}
