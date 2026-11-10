package core.handlers;

import core.Context;
import core.Handler;

public class Echo implements Handler {
    @Override
    public boolean handle(Context context) {
        return handle(context, 0);
    }

    @Override
    public boolean handle(Context context, int depth) {
        return false;
    }
}
