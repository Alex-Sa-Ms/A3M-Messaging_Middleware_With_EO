package pt.uminho.di.a3m.core.messaging;

import com.google.gson.Gson;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.*;

public class SerializableMap {
    private static Gson gson = null;
    private final Map<String, PrimitiveMapOuterClass.Value> map;
    public SerializableMap(){
        this.map = new HashMap<>();
    }
    private SerializableMap(Map<String, PrimitiveMapOuterClass.Value> map) {
        this.map = map;
    }

    public Set<Map.Entry<String, PrimitiveMapOuterClass.Value>> entrySet() {
        return map.entrySet();
    }

    public boolean hasInt(String option){
        PrimitiveMapOuterClass.Value value = map.get(option);
        return value != null && value.hasIntValue();
    }
    public boolean hasFloat(String option){
        PrimitiveMapOuterClass.Value value = map.get(option);
        return value != null && value.hasFloatValue();
    }
    public boolean hasDouble(String option){
        PrimitiveMapOuterClass.Value value = map.get(option);
        return value != null && value.hasDoubleValue();
    }
    public boolean hasString(String option){
        PrimitiveMapOuterClass.Value value = map.get(option);
        return value != null && value.hasStringValue();
    }
    public boolean hasBool(String option){
        PrimitiveMapOuterClass.Value value = map.get(option);
        return value != null && value.hasBoolValue();
    }
    public <T> boolean hasJson(String option, Class<T> objectClass){
        if(gson == null)
            gson = new Gson();
        PrimitiveMapOuterClass.Value value = map.get(option);
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
        PrimitiveMapOuterClass.Value value = map.get(option);
        if(value == null || !value.hasIntValue())
            return PrimitiveMapOuterClass.Value.getDefaultInstance()
                                               .getIntValue();
        return map.get(option).getIntValue();
    }
    public float getFloat(String option){
        PrimitiveMapOuterClass.Value value = map.get(option);
        if(value == null || !value.hasFloatValue())
            return PrimitiveMapOuterClass.Value.getDefaultInstance()
                    .getFloatValue();
        return map.get(option).getFloatValue();
    }
    public double getDouble(String option){
        PrimitiveMapOuterClass.Value value = map.get(option);
        if(value == null || !value.hasDoubleValue())
            return PrimitiveMapOuterClass.Value.getDefaultInstance()
                    .getDoubleValue();
        return map.get(option).getDoubleValue();
    }
    public String getString(String option){
        PrimitiveMapOuterClass.Value value = map.get(option);
        if(value == null || !value.hasStringValue())
            return PrimitiveMapOuterClass.Value.getDefaultInstance()
                    .getStringValue();
        return map.get(option).getStringValue();
    }
    public boolean getBool(String option){
        PrimitiveMapOuterClass.Value value = map.get(option);
        if(value == null || !value.hasBoolValue())
            return PrimitiveMapOuterClass.Value.getDefaultInstance()
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
        map.put(option, PrimitiveMapOuterClass.Value.newBuilder().setIntValue(i).build());
    }
    public void putFloat(String option, float f){
        map.put(option, PrimitiveMapOuterClass.Value.newBuilder().setFloatValue(f).build());
    }
    public void putDouble(String option, double d){
        map.put(option, PrimitiveMapOuterClass.Value.newBuilder().setDoubleValue(d).build());
    }
    public void putString(String option, String s){
        // if string is null, don't do anything.
        if(s == null) return;
        map.put(option, PrimitiveMapOuterClass.Value.newBuilder().setStringValue(s).build());
    }
    public void putBool(String option, boolean b){
        map.put(option, PrimitiveMapOuterClass.Value.newBuilder().setBoolValue(b).build());
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
        return PrimitiveMapOuterClass.PrimitiveMap.newBuilder()
                                                  .putAllItems(map)
                                                  .build()
                                                  .toByteArray();
    }

    public static SerializableMap deserialize(byte[] array) throws InvalidProtocolBufferException {
        return new SerializableMap(
                PrimitiveMapOuterClass.PrimitiveMap.parseFrom(array)
                                                   .getItemsMap());
    }
}