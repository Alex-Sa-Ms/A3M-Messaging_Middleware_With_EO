package pt.uminho.di.a3m.core.options;

public interface OptionHandler<Option> {
    Option get();
    void set(Object value);
}
