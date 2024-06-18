package basicVersion;

import basicVersion.ReceiverBasico;

public class SenderBasico {
    public int maxCredits;
    public int credits;
    public int outTotal = 0;
    public ReceiverBasico receiver;

    public SenderBasico(int maxCredits){
        this.maxCredits = maxCredits;
        this.credits = maxCredits;
    }

    public void setReceiver(ReceiverBasico receiver){
        this.receiver = receiver;
    }

    public void sendMsg(){
        if(credits > 0){
            credits--;
            outTotal++;
            receiver.receiveMsg();
        }
    }

    public void setMaxCredits(int newMaxCredits){
        credits -= maxCredits - newMaxCredits;
        maxCredits = newMaxCredits;
        receiver.setRecover(outTotal);
    }

    public void receiveCreditsBatch(int credits){
        this.credits += credits;
    }
}
