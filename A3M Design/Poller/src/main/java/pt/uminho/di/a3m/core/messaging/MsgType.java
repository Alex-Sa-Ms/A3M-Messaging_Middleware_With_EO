package pt.uminho.di.a3m.core.messaging;

public class MsgType {
    public static final byte ERROR = 0x01; // Error message
    public static final byte LINK = 0x02; // Link request
    public static final byte LINKACK = 0x03; // Link acknowledgment - Reserved for assymetric linking protocols
    public static final byte UNLINK = 0x04; // Link termination
    public static final byte FLOW = 0x05; // Link flow control credits message - to provide/remove credits from sender
    public static final byte DATA = 0x06; // Data message
    public static final byte CONTROL = 0x07; // Control message
}
