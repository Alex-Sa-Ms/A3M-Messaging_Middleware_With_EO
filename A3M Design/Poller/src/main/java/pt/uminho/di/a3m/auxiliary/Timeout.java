package pt.uminho.di.a3m.auxiliary;

public class Timeout {
    /**
     * Calculates end time based on the given timeout.
     * @param timeout timeout in milliseconds
     * @return <p> - end time if timeout is not null and positive.
     * <p> - null if timeout is null.
     * <p> - 0L if timeout is zero or negative.
     */
    public static Long calculateEndTime(Long timeout){
        Long endTime = timeout;
        if(timeout != null) {
            if (timeout > 0){
                try {
                    endTime = Math.addExact(System.currentTimeMillis(), timeout);
                }catch (ArithmeticException ae){
                    endTime = Long.MAX_VALUE;
                }
            }else {
                endTime = 0L;
            }
        }
        return endTime;
    }

    /**
     * Calculates timeout from the provided end time (deadline).
     * @param endTime timestamp, obtained using System.currentTimeMillis(),
     *                that marks the end of the timeout.
     * @return null if 'endTime' is null, or the difference between
     * the current time and the provided 'endTime'.
     */
    public static Long calculateTimeout(Long endTime){
        if(endTime == null)
            return null;
        else
            return endTime - System.currentTimeMillis();
    }

    /**
     * Returns if the deadline has been reached.
     * @param endTime deadline value
     * @return "true" if the deadline has been reached.
     * "false" if the provided "endTime" is null or
     * if the deadline has not been reached yet.
     */
    public static boolean hasTimedOut(Long endTime){
        if(endTime == null)
            return false;
        else
            return endTime - System.currentTimeMillis() <= 0;
    }
}
