package a;

class Walker {
    void walk(int depth) {
        step(depth);
    }

    void run(int depth) {
    }

    void run(int depth, int limit) {
    }

    void step(int depth) {
        b.Util.log(depth);
    }
}
