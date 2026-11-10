package app;

import core.*;
import core.handlers.Echo;

import static util.Strings.trim;

public class Walker extends Base {
    private final Handler[] extra = {};

    public Walker() {
        super(Context.create());
    }

    public void walk(java.util.List<Handler> handlers) {
        for (Handler handler : handlers) {
            handler.handle(context);
        }
        for (var handler : handlers) {
            handler.handle(context, 1);
        }
        extra[0].handle(context);
        context.first().handle(context);
        context.registry.register(new Echo());
        context.registry.register(new Context.Entry("echo", new Echo()));
        handlers.forEach(context.registry::register);
        log(trim(" walked "));
        core.Context.create("a", "b");
        unknown.call();
    }
}
