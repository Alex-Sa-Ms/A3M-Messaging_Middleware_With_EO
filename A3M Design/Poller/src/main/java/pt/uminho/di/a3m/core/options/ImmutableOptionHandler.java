package pt.uminho.di.a3m.core.options;

public class ImmutableOptionHandler<Option> implements OptionHandler<Option>{
    private final Option value;
    public ImmutableOptionHandler(Option value) {
        this.value = value;
    }

    @Override
    public Option get() {
        return value;
    }

    @Override
    public void set(Object value) {
        throw new UnsupportedOperationException("This option cannot be changed.");
    }
}
