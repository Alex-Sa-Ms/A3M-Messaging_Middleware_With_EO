package final_version.msgs;

public class WindowSizeMsg extends Msg {
    int windowSize;

    public WindowSizeMsg(int windowSize) {
        this.windowSize = windowSize;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }
}
