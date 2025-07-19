package pt.uminho.di.a3m.core.options;

public class NullableOptionHandler<Option> extends GenericOptionHandler<Option> {
    public NullableOptionHandler(Option value, Class<Option> optionClass) {
        super(value, optionClass);
    }
    @Override
    public void set(Object value) {
        if(value != null)
            this.value = optionClass.cast(value);
        else
            this.value = null;
    }
}
