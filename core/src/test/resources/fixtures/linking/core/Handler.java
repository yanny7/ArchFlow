package core;

public interface Handler {
    boolean handle(Context context);

    boolean handle(Context context, int depth);
}
