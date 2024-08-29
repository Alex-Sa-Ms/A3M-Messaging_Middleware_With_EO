package pt.uminho.di.a3m.core.messaging.payloads;

import com.google.gson.Gson;
import com.google.protobuf.InvalidProtocolBufferException;
import pt.uminho.di.a3m.core.messaging.payloads.CoreMessages.*;

import java.util.*;

public class SerializableMap {
    private static Gson gson = null;
    private final Map<String, PValue> map;
    public SerializableMap(){
        this.map = new HashMap<>();
    }
    private SerializableMap(Map<String, PValue> map) {
        this.map = map;
    }

    public Set<Map.Entry<String, PValue>> entrySet() {
        return map.entrySet();
    }

    public boolean hasInt(String option){
        PValue value = map.get(option);
        return value != null && value.hasIntValue();
    }
    public boolean hasFloat(String option){
        PValue value = map.get(option);
        return value != null && value.hasFloatValue();
    }
    public boolean hasDouble(String option){
        PValue value = map.get(option);
        return value != null && value.hasDoubleValue();
    }
    public boolean hasString(String option){
        PValue value = map.get(option);
        return value != null && value.hasStringValue();
    }
    public boolean hasBool(String option){
        PValue value = map.get(option);
        return value != null && value.hasBoolValue();
    }
    public <T> boolean hasJson(String option, Class<T> objectClass){
        if(gson == null)
            gson = new Gson();
        PValue value = map.get(option);
        if(value == null || !value.hasStringValue())
            return false;
        String json = value.getStringValue();
        try {
            gson.fromJson(json, objectClass);
            return true;
        }catch (Exception e){
            // when conversion fails
            return false;
        }
    }

    public int getInt(String option){
        PValue value = map.get(option);
        if(value == null || !value.hasIntValue())
            return PValue.getDefaultInstance()
                                               .getIntValue();
        return map.get(option).getIntValue();
    }
    public float getFloat(String option){
        PValue value = map.get(option);
        if(value == null || !value.hasFloatValue())
            return PValue.getDefaultInstance()
                    .getFloatValue();
        return map.get(option).getFloatValue();
    }
    public double getDouble(String option){
        PValue value = map.get(option);
        if(value == null || !value.hasDoubleValue())
            return PValue.getDefaultInstance()
                    .getDoubleValue();
        return map.get(option).getDoubleValue();
    }
    public String getString(String option){
        PValue value = map.get(option);
        if(value == null || !value.hasStringValue())
            return PValue.getDefaultInstance()
                    .getStringValue();
        return map.get(option).getStringValue();
    }
    public boolean getBool(String option){
        PValue value = map.get(option);
        if(value == null || !value.hasBoolValue())
            return PValue.getDefaultInstance()
                    .getBoolValue();
        return map.get(option).getBoolValue();
    }

    public <T> T getJson(String option, Class<T> objectClass){
        if(gson == null)
            gson = new Gson();
        String json = getString(option);
        try {
            return gson.fromJson(json, objectClass);
        }catch (Exception e){
            // when conversion fails
            return null;
        }
    }

    public void putInt(String option, int i){
        map.put(option, PValue.newBuilder().setIntValue(i).build());
    }
    public void putFloat(String option, float f){
        map.put(option, PValue.newBuilder().setFloatValue(f).build());
    }
    public void putDouble(String option, double d){
        map.put(option, PValue.newBuilder().setDoubleValue(d).build());
    }
    public void putString(String option, String s){
        // if string is null, don't do anything.
        if(s == null) return;
        map.put(option, PValue.newBuilder().setStringValue(s).build());
    }
    public void putBool(String option, boolean b){
        map.put(option, PValue.newBuilder().setBoolValue(b).build());
    }
    public void putJson(String option, Object jsonObject){
        // if json object is null, don't do anything.
        if(jsonObject == null)
            return;
        if(gson == null)
            gson = new Gson();
        String json = gson.toJson(jsonObject);
        putString(option, json);
    }

    public byte[] serialize(){
        return PMap.newBuilder().putAllItems(map)
                                .build()
                                .toByteArray();
    }

    public static SerializableMap deserialize(byte[] array) throws InvalidProtocolBufferException {
        return array != null ?
                new SerializableMap(PMap.parseFrom(array).getItemsMap())
                : null;
    }

    @Override
    public String toString() {
        return "SMap{" + map + '}';
    }
}