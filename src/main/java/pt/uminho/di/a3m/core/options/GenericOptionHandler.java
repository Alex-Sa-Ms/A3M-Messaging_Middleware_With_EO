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
        if(value != null)
            this.value = optionClass.cast(value);
        else
            throw new IllegalArgumentException("null value is not allowed.");
    }
}