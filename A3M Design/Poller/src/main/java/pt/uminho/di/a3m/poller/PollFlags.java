package pt.uminho.di.a3m.poller;
public class PollFlags {

    // ***** Single bit flags ***** //
    /** Read event bit */
    public static final int POLLIN = 0x01;

    /** Write event bit */
    public static final int POLLOUT = 0x02;

    /** Error event bit */
    public static final int POLLERR = 0x04;

    /** Hang up event bit */
    public static final int POLLHUP = 0x08;

    /**
     * Free event bit.
     * <p> Primarily used to inform pollers
     * that they need to delete the wait queue entry
     * since the pollable is being "freed", i.e. closed.
     * This flag is for internal use, so it should not
     * be passed in a poll() callback., instead it should
     * be passed through the wake-up callback. The user
     * may detect this situation by receiving POLLHUP
     * and by not being able to execute the operations it
     * could. For instance, the POLLIN flag could be passed
     * so that the reading method could fail and indicate
     * the closure of the pollable.
     */
    public static final int POLLFREE = 0x10;

    /**
     * <p> Exclusive bit.
     * <p> Used to prevent thundering herd on pollables.
     * Between all the waiters of the same pollable, all waiters
     * without this flag and at least one waiter using it will be notified. 
     * <p> This flag only matters when used in conjunction with POLLET,
     * therefore it most not be used with level-triggered events.
     * @implNote An event mask that contains this flag and is used to add
     * a pollable to a poller cannot be changed using the modify() method.
     * The first reason is that it determines how the poller instance is added
     * to the wait queue of a pollable, i.e., if the entry is added as exclusive or not.
     * The second reason is that an exclusive entry which the wake-up function returns
     * success, meaning it as accepted the task of handling the event(s), and then decides
     * to change its events of interest, may result in an event that should have been handled
     * to be dismissed. By dismissing the event(s), an exclusive waiter that would handle it/them
     * will not be woken up until a new wake-up call, therefore leading to inefficiency.
     * In the worst case, because the event was not handled, a deadlock may occur.
     */
    public static final int POLLEXCLUSIVE = 0x01 << 29;

    /**
     * <p>One shot bit. Only meaningful when registering interest
     * on poller instances.
     * <p>Compatible with both edge-trigger and
     * level-trigger approaches. This flag is used to disarm
     * the interest in being informed of events for the
     * particular pollable the flag was set. Essentially,
     * it clears all event-related bits so that the poller
     * does not inform about the readiness of the pollable
     * until the events of interest are set again through
     * the modify() method.
     */
    public static final int POLLONESHOT = 0x01 << 30;

    /**
     * <p>Edge-Trigger bit. Only meaningful when registering interest
     * on poller instances. If set, the poller should
     * face the events as edge-triggered, otherwise,
     * the events are level-triggered.
     * <p>To further elaborate on how this flag actually works
     * for the poller instances, what this flag does is inform
     * only one of waiters (on the poller instance) about the 
     * the availability of events of interest for the pollable
     * registered with this flag, upon which the availability of
     * the pollable is removed so that the remaining waiters do
     * not perceive the pollable as available. However, if after
     * removing the pollable from the ready list, another event
     * arrives for the same pollable, another waiter will perceive
     * the pollable as available. If avoiding this situation is desirable,
     * one should register the pollable with an additional flag, the POLLONESHOT,
     * which will remove the interest in receiving events for the pollable
     * until they are rearmed through the modify() method of the poller instance.
     * </p>
     */
    public static final int POLLET = 0x01 << 31;

    // ***** Flag combinations ***** //
    public static final int POLLINOUT_BITS =
            POLLIN | POLLOUT;

    /**
     * Private bits of the events mask.Only
     * meaningful for poller instances.
     * Combination of flags that dictate how the
     * waiters should be notified.
      */
    public static final int POLLER_PRIVATE_BITS =
            POLLET | POLLONESHOT | POLLEXCLUSIVE;

    /**
     * Bits compatible with the POLLEXCLUSIVE flag.
     */
    public static final int POLLER_EXCLUSIVE_OK_BITS =
            POLLINOUT_BITS | POLLERR | POLLHUP | POLLET | POLLEXCLUSIVE;
}
