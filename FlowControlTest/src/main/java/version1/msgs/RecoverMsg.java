package version1.msgs;

public class RecoverMsg extends Msg{
    int totalCredits;

    public RecoverMsg(int totalCredits) {
        this.totalCredits = totalCredits;
    }

    public int getTotalCredits() {
        return totalCredits;
    }

    public void setTotalCredits(int totalCredits) {
        this.totalCredits = totalCredits;
    }
}
