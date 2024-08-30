package pt.uminho.di.a3m.core.linking;

import pt.uminho.di.a3m.core.messaging.MsgType;
import pt.uminho.di.a3m.core.messaging.Payload;
import pt.uminho.di.a3m.core.messaging.payloads.SerializableMap;

public class LinkAckPayload implements Payload {
    private SerializableMap map = null;

    public LinkAckPayload(SerializableMap map) {
        this.map = map;
    }

    public SerializableMap getMap() {
        return map;
    }

    @Override
    public byte getType() {
        return MsgType.LINKACK;
    }

    @Override
    public byte[] getPayload() {
        return map != null ? map.serialize() : null;
    }

    public boolean parseFrom(byte type, byte[] payload) {
        boolean ret = false;
        if(type != MsgType.LINKACK){
            try{
                map = SerializableMap.deserialize(payload);
                ret = check();
            }catch (Exception ignored){}
        }
        return ret;
    }

    /**
     * @return true if all required information (without metadata) is present
     * in the link request. false, otherwise.
     */
    public boolean check(){
        return !(map == null
                || !map.hasInt("ackCode")
                || !map.hasInt("clockId"));
    }

    /**
     * @return true if it is a valid LINKACK message and
     * check that it does not have metadata.
     */
    public boolean checkWithoutMetadata(){
        return map != null
                && map.size() == 2
                && map.hasInt("ackCode")
                && map.hasInt("clockId");
    }

    /**
     * @return true if all required information (including metadata) is present
     * in the link request. false, otherwise.
     */
    public boolean checkWithMetadata(){
        return !(map == null
                || !map.hasInt("ackCode")
                || !map.hasInt("protocolId")
                || !map.hasInt("clockId")
                || !map.hasInt("credits"));
    }
}
