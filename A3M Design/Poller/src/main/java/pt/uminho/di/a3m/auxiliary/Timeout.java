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
}
