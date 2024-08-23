package pt.uminho.di.a3m.core.flowcontrol;

public class OutFlowControlState {
    private int outCredits = 0;

    public OutFlowControlState(int outCredits) {
        this.outCredits = outCredits;
    }

    public int getCredits() {
        return outCredits;
    }

    public boolean hasCredits(){
        return outCredits > 0;
    }

    public int applyCreditVariation(int credits){
        outCredits += credits;
        return outCredits;
    }

    public boolean trySend(){
        if(outCredits > 0) {
            outCredits--;
            return true;
        }else return false;
    }
}
