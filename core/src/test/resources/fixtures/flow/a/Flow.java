package a;

import java.util.List;

class Flow extends Base {
    private final Sink sink;

    Flow(Sink sink) {
        super(sink.name());
        this.sink = sink;
    }

    Flow() {
        this(new Sink());
    }

    void branches(int kind, Object value) {
        if (kind > 2) {
            sink.big(kind);
        } else if (kind < 0) {
            return;
        } else if (sink.accepts(kind)) {
            sink.small(kind);
        } else {
            throw new IllegalStateException(sink.name());
        }
        switch (kind) {
            case 1, 2 -> sink.few();
            case 3 -> {
            }
            default -> sink.many();
        }
        int size = value instanceof List<?> list ? list.size() : sink.size();
    }

    void loops(List<Sink> sinks) {
        for (int i = 0; i < sinks.size(); i++) {
            sinks.get(i).flush();
        }
        for (Sink each : sinks) {
            each.flush();
        }
        while (sink.accepts(0)) {
            sink.drain();
        }
        do {
            sink.drain();
        } while (sink.accepts(1));
    }

    void expressions(List<Sink> sinks) {
        sink.write(sink.name().trim(), this.helper());
        sinks.forEach(s -> {
            if (s.accepts(0)) {
                return;
            }
            s.flush();
        });
        sinks.stream().map(Sink::name).forEach(sink::write);
        try {
            sink.open();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } finally {
            sink.close();
        }
    }

    private String helper() {
        return "";
    }
}
