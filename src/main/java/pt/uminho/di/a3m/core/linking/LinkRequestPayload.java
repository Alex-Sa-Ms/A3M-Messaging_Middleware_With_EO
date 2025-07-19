package pt.uminho.di.a3m.core.linking;

import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.payloads.SerializableMap;

public class LinkRequestPayload implements Payload {
    private SerializableMap map = null;

    public LinkRequestPayload(SerializableMap map) {
        this.map = map;
    }

    public SerializableMap getMap() {
        return map;
    }

    @Override
    public byte getType() {
        return MsgType.LINK;
    }

    @Override
    public byte[] getPayload() {
        return map != null ? map.serialize() : null;
    }

    public boolean parseFrom(byte type, byte[] payload) {
        boolean ret = false;
        if(type != MsgType.LINK){
            try{
                map = SerializableMap.deserialize(payload);
                ret = check();
            }catch (Exception ignored){}
        }
        return ret;
    }

    /**
     * @return true if all required information is present
     * in the link request. false, otherwise.
     */
    public boolean check(){
        return !(map == null
                || !map.hasInt("protocolId")
                || !map.hasInt("clockId")
                || !map.hasInt("credits"));
    }
}
