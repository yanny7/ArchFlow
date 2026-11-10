package core;

import java.util.List;

public class Context {
    public final Registry registry = new Registry();
    List<Handler> handlers;

    public Handler first() {
        return handlers.get(0);
    }

    public static Context create(String... names) {
        return new Context();
    }

    public record Entry(String name, Handler handler) {
        public String name() {
            return name.trim();
        }
    }

    public static class Registry {
        public void register(Handler handler) {
        }

        public void register(Entry entry) {
            register(entry.handler());
            entry.name();
        }
    }
}
