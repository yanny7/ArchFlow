package a;

class Walker {
    void walk(int depth) {
        step(depth);
    }

    void step(int depth) {
        b.Util.log(depth);
    }
}
