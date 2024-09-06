package pt.uminho.di.a3m.core.messaging.payloads;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.uminho.di.a3m.core.messaging.payloads.CoreMessages;
import pt.uminho.di.a3m.core.messaging.payloads.SerializableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class SerializableMapTest {
    SerializableMap map;

    @BeforeEach
    void initMap(){
        map = new SerializableMap();
    }

    @Test
    void testInt() {
        // assert that when a value is not present, it gives the default value of the requested type
        assert map.getInt("var") == CoreMessages.PValue.getDefaultInstance().getIntValue();
        // assert integer "var" is not prevent
        assert !map.hasInt("var");
        // put float "var" and assert an integer "var" is not present
        map.putFloat("var", 1f);
        assert !map.hasInt("var");
        // assert the default value is still given when the requested type is not present
        assert map.getInt("var") == CoreMessages.PValue.getDefaultInstance().getIntValue();
        // put and assert insertion
        map.putInt("var", 1);
        assert map.hasInt("var");

    }

    @Test
    void testFloat() {
        // assert float "var" is not prevent
        assert !map.hasFloat("var");
        // put integer "var" and assert an float "var" is not present
        map.putInt("var", 1);
        assert !map.hasFloat("var");
        // put and assert insertion
        map.putFloat("var", 1f);
        assert map.hasFloat("var");
    }

    @Test
    void testDouble() {
        // assert double "var" is not prevent
        assert !map.hasDouble("var");
        // put float "var" and assert a double "var" is not present
        map.putFloat("var", 1f);
        assert !map.hasDouble("var");
        // put and assert insertion
        map.putDouble("var", 1L);
        assert map.hasDouble("var");
    }

    @Test
    void testString() {
        // assert string "var" is not prevent
        assert !map.hasString("var");
        // put float "var" and assert a string "var" is not present
        map.putFloat("var", 1f);
        assert !map.hasString("var");
        // put and assert insertion
        map.putString("var", "Something");
        assert map.hasString("var");
    }

    @Test
    void testBool() {
        // assert boolean "var" is not prevent
        assert !map.hasBool("var");
        // put float "var" and assert a boolean "var" is not present
        map.putFloat("var", 1f);
        assert !map.hasBool("var");
        // put and assert insertion
        map.putBool("var", true);
        assert map.hasBool("var");
    }

    @Test
    void testJson() {
        // assert a Person Json "var" is not prevent
        boolean ret = !map.hasJson("var", Person.class);
        assert ret;
        // put float "var" and assert a json "var" is not present
        map.putFloat("var", 1f);
        ret = !map.hasJson("var", Person.class);
        assert ret;
        // put and assert insertion
        Person person = new Person("Alex", 23);
        map.putJson("var", person);
        ret = map.hasJson("var", Person.class);
        assert ret;
        // assert "var" is not a Car Json
        ret = !map.hasJson("var", Car.class);
        assert !ret;
    }

    @Test
    void serializeAndDeserialize() throws InvalidProtocolBufferException {
        map.putInt("int",1);
        map.putFloat("float",1f);
        map.putDouble("double",1L);
        map.putBool("bool",true);
        map.putString("string","Hey");
        Person person = new Person("John", 43);
        person.addCar(new Car("ModelA", 2012, "Plate1"));
        person.addCar(new Car("ModelB", 2023, "Plate2"));
        map.putJson("json", person);

        // serialize and deserialize map
        SerializableMap map2 = SerializableMap.deserialize(map.serialize());

        // ensure maps are equal
        assert map.getInt("int") == 1;
        assert map.getFloat("float") == 1f;
        assert map.getDouble("double") == 1L;
        assert map.getBool("bool") == true;
        assert map.getString("string").equals("Hey");
        assert map.getJson("json", Person.class).equals(person);
    }

    public static class Person{
        private String name;
        private int age;
        private List<Car> cars = new ArrayList<>();

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public List<Car> getCars() {
            return cars;
        }

        public void setCars(List<Car> cars) {
            this.cars = cars;
        }

        public void addCar(Car car){
            this.cars.add(car);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;

            Person person = (Person) object;

            if (age != person.age) return false;
            if (!Objects.equals(name, person.name)) return false;
            return Objects.equals(cars, person.cars);
        }
    }

    public static class Car{
        private String model;
        private int year;
        private String plate;

        public Car(String model, int year, String plate) {
            this.model = model;
            this.year = year;
            this.plate = plate;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getYear() {
            return year;
        }

        public void setYear(int year) {
            this.year = year;
        }

        public String getPlate() {
            return plate;
        }

        public void setPlate(String plate) {
            this.plate = plate;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;

            Car car = (Car) object;

            if (year != car.year) return false;
            if (!Objects.equals(model, car.model)) return false;
            return Objects.equals(plate, car.plate);
        }
    }
}