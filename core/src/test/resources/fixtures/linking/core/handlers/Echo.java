package core.handlers;

import core.Context;
import core.Handler;
import core.Visitor;

public class Echo implements Handler, Visitor<Context> {
    @Override
    public boolean handle(Context context) {
        return handle(context, 0);
    }

    @Override
    public boolean handle(Context context, int depth) {
        return false;
    }

    @Override
    public void visit(Context value) {
        handle(value);
    }
}
