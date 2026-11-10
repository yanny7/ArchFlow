package core;

public abstract class Base {
    protected final Context context;

    protected Base(Context context) {
        this.context = context;
    }

    protected void log(String message) {
    }
}
