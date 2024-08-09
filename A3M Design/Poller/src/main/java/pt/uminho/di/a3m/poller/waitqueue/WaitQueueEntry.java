package pt.uminho.di.a3m.poller.waitqueue;

public class WaitQueueEntry {
    private WaitQueueEntryImpl entry = null;
    private WaitQueue queue;

    protected WaitQueueEntry(WaitQueue queue) {
        if(queue == null)
            throw new IllegalArgumentException("Queue must not be null");
        this.queue = queue;
    }

    protected WaitQueueEntryImpl getEntry() {
        return entry;
    }

    protected WaitQueue getQueue() {
        return queue;
    }

    protected void setEntry(WaitQueueEntryImpl entry) {
        this.entry = entry;
    }

    protected void setQueue(WaitQueue queue) {
        this.queue = queue;
    }

    private String addErrorString(WaitQueueEntryImpl entry){
        String err = "The entry could not be added: ";
        if(entry != null)
            err += "An entry has been added already.";
        else
            err += "After deletion, a new entry cannot be added.";
        return err;
    }

    public void add(WaitQueueFunc func, Object priv){
        if(entry == null && queue != null) {
            queue.addEntry(this, func, priv);
        }else throw new IllegalStateException(addErrorString(entry));
    }

    public void addExclusive(WaitQueueFunc func, Object priv){
        if(entry == null && queue != null) {
            queue.addExclusiveEntry(this, func, priv);
        }else throw new IllegalStateException(addErrorString(entry));
    }

    public void delete(){
        if(entry != null)
            queue.deleteEntry(this);
    }
}
