package final_version.msgs;

public class CreditsMsg extends Msg {
    int credits;

    public CreditsMsg(int credits) {
        this.credits = credits;
    }

    public int getCredits() {
        return credits;
    }

    public void setCredits(int credits) {
        this.credits = credits;
    }
}
