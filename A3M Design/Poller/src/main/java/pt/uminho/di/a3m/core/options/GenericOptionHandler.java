package pt.uminho.di.a3m.core.options;

public class GenericOptionHandler<Option> implements OptionHandler<Option> {
    Option value;
    final Class<Option> optionClass;

    public GenericOptionHandler(Option value, Class<Option> optionClass) {
        this.value = value;
        if (optionClass == null)
            throw new IllegalArgumentException("Option class must not be null.");
        this.optionClass = optionClass;
    }

    @Override
    public Option get() {
        return value;
    }

    @Override
    public void set(Object value) {
        this.value = optionClass.cast(this.value.getClass().cast(value));
    }
}