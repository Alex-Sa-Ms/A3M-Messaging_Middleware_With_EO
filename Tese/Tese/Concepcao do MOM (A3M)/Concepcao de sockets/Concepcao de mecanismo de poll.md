# Introduction & Goal
In a messaging middleware, having a polling mechanism is not just a desirable feature, it's a necessity. Polling serves the critical function of determining which events are available for a given object, allowing to efficiently read data, write data, or execute other I/O operations. However, the peak of greatness is achieved when the polling mechanism has the ability to monitor events of multiple objects simultaneously.

A mechanism that enables efficient handling of multiple objects is crucial to achieve high performance and scalability. It enables a single thread to handle a large number of concurrent events as opposed to having a dedicated thread for each object of interest, thus avoiding unnecessary resource consumption and latency, in addition to ensuring higher scalability under heavy loads.

The primary objective behind designing this polling mechanism was to provide users with an efficient way to monitor multiple sockets using a single thread. This would not only provide programming flexibility but also aid in boosting performance and scalability. However, by avoiding tight coupling with the middleware sockets, the mechanism can be extended to monitor other entities, such as links or objects related to custom socket implementations, making it versatile and adaptable to a wide range of use cases.

In this section, we will delve into the specifics of how this polling mechanism works, its architecture and the process that led to its development.
# Architecture
The provided polling mechanism is a simplification and adaptation of two system calls: `poll()` and `epoll()`. (*include citation to the GitHub and pages of the manual*) For occasional polling or when the targets of the operation change frequently, the user may use `Poller.poll()`, which is analogous to the Linux kernel's `poll()`. If the polling targets remain relatively constant, opting for the mechanism inspired in `epoll()` is the most efficient option. In this case, a Poller instance is created through `Poller.create()`, the objects of interest are registered using `add()`, and the `await()` method is invoked to wait and retrieve available events. 

## Foundational Components
The following components are the cornerstone of the polling mechanism, providing the necessary structures and functionality to implement the functionalities of the `poll()` and `epoll()` adaptations. While these components are not the most important parts, they play a crucial role in providing an efficient and reliable polling mechanism.
### List Node
The `ListNode` class implements a circular doubly linked list, which is used in multiple occasions by the poller implementation, particularly in _wait queues_ to store entries and in _pollers_ to form the _ready list_. Before discussing these classes in detail, it’s important to highlight some key characteristics of the `ListNode` class.

The circular nature of this list allows for *O(1)* insertions at both the head and tail. Additionally, because it’s a doubly linked list, nodes can also be deleted in constant time (O(1)) when holding the reference to the node that should be deleted. The combination of these properties make this data structure ideal for efficiency when faced with the requirements of wait queues and pollers, as explained below.
### Wait Queue
In the diagram [[Polling Class Diagram - Wait Queue Side.png]] we observe the foundation of polling mechanism, which relies on *wait queues*. These queues manage _wait queue entries_, each corresponding to an entity interested in an event (or events) related to the queue's owner. This *waiting* system, adapted from the Linux kernel, was designed to prevent unauthorized objects from manipulating *wait queues*.

This adaptation diverges from the kernel's approach by allowing new waiters to register themselves without requiring direct access to the *wait queue* (referred as *wait_queue_head* by the kernel). By providing an initialized *wait queue entry* linked to the *queue* of interest, rather than the queue itself, unauthorized manipulation of the queue is prevented. This design ensures that waiters can only manage their own *wait queue entry* and enables the queue owner to reject a new waiter simply by not providing an initialized entry.

The process of queuing a waiter is the following:
1. The owner of the *wait queue* creates a *wait queue entry*, which remains associated with the queue throughout its lifetime. This entry is then given to the waiter that wishes to register itself in the queue.
2. The waiter, whenever desired, can add itself to the queue using either the `add()` or `addExclusive()` method.
3. The waiter is informed of available events via the `WaitQueueFunc`, which is provided when adding itself to the queue. When registering, the waiter also supplies a private object used by the function to perform actions related to the waiter. For instance, waking the waiter up, or in the case of the poller, marking the object of interest as ready for some event.
4. When the waiter is no longer interested in the events of the queue's owner, it must disassociate itself through the `delete()` method of the *wait queue entry*, upon which the entry is rendered useless and cannot be used for queuing again.

This adaptation supports two types of waiters: *non-exclusive* waiters and *exclusive* waiters, identified by the presence of the *wait flag* `EXCLUSIVE` in the entry's `waitFlags` attribute. Non-exclusive entries are registered using the `add()` method and inserted at the head of the queue. A non-exclusive entry refers to a waiter that does not mind having the event notifications shared with other entries. On the other hand, an exclusive entry refers to waiters that want exclusivity regarding notifications. These kind of entries are registered using the `addExclusive()` method and are inserted at the tail of queue.

To better understand how exclusivity works, delving into the specifics of the notification methods is needed. This implementation includes two notification methods:

The first notification method, adapted from the kernel, has the following signature: `int wakeUp(int mode, int nrExclusive, int wakeFlags, int key)`. The `mode` and `wakeFlags`, if required, are used under the queue owner semantics . The `nrExclusive` parameter specifies the maximum number of exclusive entries that can be woken up, with a value of zero or less indicating that all entries should be woken up[^1]. The `key` is used to indicate available events. This method iterates over the queue's entries, starting from the head, invoking each entry's `WaitQueueFunc` with `mode`, `wakeFlags`, `key` and their associated private object as parameters. The iteration stops when the specified number of exclusive entries have been successfully woken up or when the end of the queue is reached. In short, calling this method wakes up all non-exclusive entries (since they are inserted at the head) and a number of exclusive entries up to the value specified by `nrExclusive`.[^2]

[^1] Global wake-up calls are typically executed when the object of interest is closed. The global "close" notifications are commonly triggered by a combination of the `HUP` flag (indicating that the peer has hung up or the object has been closed) and the `FREE` flag (indicating that the object wishes to be released, requiring waiters to remove themselves from the queue so the object can close gracefully).
[^2] To ensure true exclusivity, it is crucial to avoid using the queues (or their owners) in a way that both non-exclusive and exclusive entries coexist in the queues simultaneously. This is essential to prevent non-exclusive entries from draining the available events before the exclusive entries have an opportunity to process them.

The second notification method, which I developed, corresponds to a fair version of the first method. The objective of this method is providing an equal opportunity in being notified to all exclusive entries. In the first notification method, exclusive entries are not relocated after being woken, which can lead to monopolization by the first few entries in the queue. For instance, if we assume the most common scenario, characterized by `nrExclusive` equal to 1, we can verify that the entries that follow the first exclusive entry do not get a chance of being notified unless the `WaitQueueFunc` of the exclusive entries removes the entry from the queue or the return value indicates an unsuccessful wake up.  For scenarios where the monopolization of events is not desired, this second notification method addresses this issue by moving successfully woken up entries to the tail of the queue when their `WaitQueueFunc` does not result in their removal (deletion), and thus ensuring that all exclusive entries have an equal opportunity to be notified.
### Park State
Although the `WaitQueueFunc` does not necessarily need to wake up a specific thread, in some scenarios, such functionality is required. The Linux Kernel employs a rather complex wake-up function that interacts with the state of threads, their corresponding tasks and scheduling. Due to the low-level nature of these operations, replicating this behavior in the high-level programming environment of Java is not feasible. Consequently, finding an alternative solution was necessary to wake specific threads. 

The alternative solution I devised takes advantage of the `LockSupport` mechanism which is complemented by a `ParkState` instance. 

The `LockSupport` mechanism is characterized by two operations: parking and unparking. A thread that needs to wait for an event may invoke `park()` and wait for another thread - which is typically the manager of the event - to `unpark()` it. While the mechanism is simple, the sole use of this mechanism is not enough for our purposes. It's important to note that unparking a thread that has not yet invoked `park()` results in the accumulation of a permit. Further invocations of `unpark()` will not generate additional permits, as the accumulation is capped at one permit. When a thread invokes `park()`, it checks for the existence of a permit. If a permit is present, it is consumed, allowing the thread to proceed without blocking. If a permit is not available, the thread waits until one is provided via `unpark()` or, if a deadline is provided, until the timestamp is reached.

To effectively use `LockSupport`, I create a `ParkState` class, which is also present in the [[Polling Class Diagram - Wait Queue Side.png]] diagram. Each instance of `ParkState` includes two attributes: a reference to the thread to be woken up and an atomic boolean representing the thread's parking state. Before parking, a thread must set its parking state to "true" indicating its intention to park. This can be done immediately before parking, or when the parking of the thread is anticipated and the accumulation of a permit is desirable. With the parking state set to "true", when the wake-up condition is met, such as the availability of events in the polling mechanism, the responsible thread checks the parking state and grants permission to unpark when the state indicates that the thread is parked. If the thread is not perceived as parked, the wake-up attempt is unsuccessful, as the thread was not in a state to be woken up.

![[Polling Class Diagram - Wait Queue Side.png]]

## Shared Architectural Polling Concepts
The architecture of the polling mechanism relies on several core concepts that are shared between the `poll()` and `epoll()` adaptations. In this section, we will explore these key concepts, which form the backbone of the polling system's architecture.
### Poll Flags
Poll flags are essential components within the polling mechanism, serving multiple purposes: event-related flags signal specific conditions on _pollable_ objects, while mode-related flags determine the behavior of the polling operation. Understanding the role and functionality of these flags is crucial for effectively managing and interpreting the events during the polling process.

The **event-related flags** are used by pollers - caller of `poll()` - to manifest the events of interest and by *pollable* objects to indicate their current status, such as readiness for reading, writing, or encountering an error. The available event flags are:
- `POLLIN`:
	Associated with read operations.

- `POLLOUT`:
	Associated with write operations.

- `POLLERR`:
	Associated with error or exception conditions. An example could be to report when the read end of the *pollable* object has been closed.
	
	*Note:* The polling mechanism always reports this event regardless whether it was specified as an event of interest or not.

- `POLLHUP`:
	Associated with hang up. Used to signal that a peer closed the connection or that the socket was closed. Depending on the *pollable*'s semantics, this event does not necessarily mean that operations like reading are impossible. There may still be data available to read after the *pollable* is closed.
	
	_Note:_ Like `POLLERR`, this event is always reported regardless of interest.

- `POLLFREE`:
	Special event flag used by *pollable* objects to notify their waiters of the will to be released. When a *pollable* is closed, the `POLLFREE` and `POLLHUP` flags are reported to all waiters, through their respective `WaitQueueFunc`, signaling them to delete their *wait queue entry* as no more events will be reported.
	
	*Note:* This flag is exclusively used in the scenario described above and has no effect when provided in an events of interest mask since it should not be returned by a `poll()` method.

The **mode-related flags**, which are only used with `Poller` instances, define the polling behavior:
- `POLLET`:
	Registers events in edge-triggered mode, meaning the `Poller` instance only notifies when the perceived state of events changes from "no events" to "events available".  This contrasts with level-triggered mode, where the `Poller` continually reports the _pollable_ as available as long as operations remain possible. 
	
	To fully grasp this concept, it is essential to understand that the triggering mode is based on the `Poller` instance's perception of the *pollables* which is updated based on notifications. While the `Poller` instance might perceive a *pollable* as unavailable, the *pollable* itself could still be available for operations.
	
	_Note:_ In edge-triggered mode, notified waiters should use non-blocking methods to handle available operations until those operations indicate they would block. This is essential to ensure a *pollable* does not remain idle because a waiter neglected its duties and a new waiter could not be notified because an event notification was not issued to change the *poller*'s perceived state of the *pollable* from "no events" to "events available".

- `POLLONESHOT`:
	Disarms interest in a _pollable_ after it is notified of an event. All event-related bits are cleared from the events of interest mask, which effectively halts any further notifications from the `Poller` instance regarding the _pollable_. To resume the receiving those notifications, the events of interest must be rearmed through the `modify()` method. 
	
	This flag is compatible with both edge-trigger and level-trigger modes.
	
	This flag is useful for gaining precise control over event notifications, especially in conjunction with `POLLET`. In edge-triggered mode, when a waiter is notified, events of the same pollable are all "cleared" from the `Poller` instance, but this doesn't prevent the *pollable* from triggering new notifications. This means that another waiter might be woken up by the `Poller` instance to handle the same events already being address by the first waiter. By using `POLLONESHOT`, we can ensure that only one of the *poller*'s waiters is notified to handle the events of a *pollable*.

- `POLLEXCLUSIVE`:
	Prevents the thundering herd problem by registering the `Poller` instance as an _exclusive_ waiter of the *pollable* of interest. This flag is relevant only when used with edge-triggered events (`POLLET`) and is incompatible with `POLLONESHOT`. It ensures that only one waiter is notified of an event, reducing the risk of resource contention.  
	
	_Note:_ Any attempt of modifying a registered event mask when the `POLLEXCLUSIVE` flag is involved will result in an exception. The key reasons behind the disapproval of these type of modifications are:
	1. If an exclusive waiter has already been successfully notified about some events and then decides to change its events of interest, it could cause other events that should be handled to be missed. This could lead to a situation where another exclusive waiter is not woken up to handle those events because the first waiter "accepted" and then dismissed the events due to a change in interest, potentially leading to inefficiency or, in the worst case, a deadlock.	
	2. A similar scenario, to the one mentioned in the first reason, can happen when attempting to convert an entry from exclusive to non-exclusive.
### Poll Table & Poll Queuing Function 

The `PollTable` is a key component in the polling mechanism, serving as the interface through which the caller of a *pollable*'s `poll()` method can register its interest in the events of the *pollable*. As the only object passed to the method, it plays a crucial role in allowing a caller to become a waiter if desired. 

The `PollTable` comprises three attributes: an event mask, a *poll queuing function*, and a private object. While the event mask indicates the events the caller is interested in, it's typically not used directly by the *pollable*. Instead, the `poll()` method of the *pollable* usually returns a bitmask of all events currently available[³], regardless of the event mask in the `PollTable`.

The poll queuing function is essential, as it handles the addition of the caller to the *pollable*'s *wait queue*. When the *pollable*'s `poll()` method determines that the conditions for adding a new waiter are met, it calls the poll queuing function with an initialized *wait queue entry*. This entry, along with the private object, helps the *poll queuing function* register the caller in the *pollable*'s *wait queue* and perform any caller-specific actions - such as adding the *pollable* to the ready list as done by the `Poller` instances - necessary for the waiter's registration. If the conditions are not met, the *pollable* may pass an uninitialized entry to indicate that queuing is not possible.

If the `PollTable` lacks a *poll queuing function*, the pollable only returns the bitmask of available events. This function is critical because it not only queues the caller as a waiter but also handles any specific needs the caller may have during the queuing process. The private object provides context or state information that may be necessary for the queuing function to operate correctly.

### Pollable
The polling mechanism operates on objects of interest, which I called _pollables_. For an object to be eligible for polling, it must implement the `Pollable` interface. This interface defines two essential methods:
1. **`Object getId()`**:
	This method retrieves the unique identifier of the _pollable_. Each _pollable_ is expected to have a unique identifier that distinguishes it among other local _pollables_. This local uniqueness is crucial for avoiding conflicts when registering interest in a _pollable_ within a `Poller` instance. `Poller` instances register each _pollable_ in a `HashMap`, using the identifier as the key. Since the `HashMap` relies on the `hashCode()` of the key for operations like insertions and look-ups, ensuring unique identifiers is crucial to minimize and avoid collisions.
2. **`int poll(PollTable pt)`**:
	This method is central to the polling mechanism, responsible for retrieving the currently available events for a _pollable_. These events are returned as a bit-mask, representing a combination of poll flags, such as `POLLIN`, `POLLOUT`, `POLLERR`, and `POLLHUP`.
	Each _pollable_ is also assumed to manage a single wait queue, with the `poll()` method being responsible for queuing waiters in that queue. Queuing the caller is only possible if a `PollQueuingFunc` and its associated private object are provided in the `PollTable` passed as a parameter. However, the presence of a queuing function does not guarantee that queuing will occur. The _pollable_ may not allow queuing under certain conditions, such as if it has been closed. In such cases, the _pollable_ must invoke the caller's queuing function with an *uninitialized wait queue entry* to inform the caller that queuing is not possible. 


![[Polling Class Diagram - Poller Side.png]]
## Adaptation of poll()

### Algorithm Overview

After covering the foundational components involved in the polling system, we can now explore how these pieces are combined in the main methods. Having this background in mind, understanding the algorithm underlying `Poller.poll()` method, which is a Java user-level adaptation of the Linux Kernel's `poll()` system call, is straightforward.

The `Poller.poll()` method is designed to monitor a set of objects of interest and detect when they become ready to perform I/O operations. This method requires a structure containing the objects to be monitored along with the corresponding events of interest mask. Additionally, a parameter identifying the maximum number of *pollable* objects with available events to be returned is requested, while a timeout value is optional.

1. **Initial Iteration and Queuing**:
    - The `Poller.poll()` method begins by iterating over the list of objects of interest. During this initial iteration, it adds itself to the *wait queue* of each object while checking whether any events are immediately available.
    - If an event is detected during this first iteration, the method ceases further queuing, by removing the *poll queuing function* from the poll table used for polling. Further queuing is unnecessary, as the method immediately prepares to return the detected event(s) to the user.
    - If the maximum number of objects with available events is reached, the iteration is stopped, the *wait queue entries* are deleted, and the events are returned to the user. 
    - If no events are found during this first iteration, the method will have registered itself as a waiter on each object by adding a *wait queue entry* to their respective *wait queues*. This ensures that it will be notified if any of the objects become ready during any following idle-wait periods.

2. **Idle-Wait and Event Detection**:
    - Once the thread is queued as a waiter for all objects, it enters an idle-wait state. In this state, the thread remains inactive until one of the following occurs:
        - An object of interest becomes ready and signals the thread.
        - The specified timeout period elapses.
        - An interrupt signal is received.
    - If the timeout is reached or an interrupt signal is detected, the method proceeds to clean up by removing the entries from all wait queues. It then returns, indicating either a timeout or interruption, as appropriate.

3. **Re-Polling After Wake-Up**:
    - If the thread is woken up by an event, the method performs another iteration over the objects of interest. This time, however, it does so with focusing solely on fetching available events, bypassing the queuing step - by not providing a *poll queuing function* - since it is already registered as a waiter.
    - During this traversal, the method collects any events that have become available, stopping when the number of maximum objects with available events is achieved or when all the objects have been polled. If any events are found, it cleans up by removing the *wait queue entries*, and returns the events to the user.

4. **Repeating the Process if Necessary**:
    - If, after being woken up, no events are found during the re-polling iteration, the process repeats. The thread re-enters the idle-wait state, waiting once more for an event, a timeout, or an interrupt signal.
    - This loop continues until either events are detected, leading to a return, or the operation is terminated by a timeout or signal.
### Comparison with the Linux Kernel's *poll()* implementation

When comparing the user-level `Poller.poll()` implementation with the Linux kernel's `poll()` system call, several key differences emerge, mostly due to the contrasting environments in which they operate.

**1. Foundational Components and Complexity**

Any significant differences between this implementation and the kernel's implementation regarding the foundational components has been highlighted in their respective sections. However, it is important to emphasize that the core algorithm underlying both implementations remains fundamentally simple, leaving limited room for deviation.

**2. Complexity and Low-Level Operations**

The kernel's `poll()` system call is inherently more complex due to its need to manage low-level details such as thread scheduling, thread states, reference counting, and efficient memory allocation. In contrast, this user-level implementation operates in a higher-level environment, which simplifies many of these concerns. For example, while the kernel meticulously makes efficient memory allocation - like allocating a page for multiple wait queue entries instead of suffering the overhead of allocating memory for each entry at a time - , the `Poller.poll()` implementation relies on a higher-level data structure, such as `List<WaitQueueEntry>`, without needing to manage memory with the same granularity.

**3. Handling of Objects of Interest**

Another significant difference lies in how objects of interest are handled. In the kernel's implementation, `poll()` works with file descriptors, which are then used to access the corresponding kernel structures, while the `Poller.poll()` implementation receives the instances of the objects to be polled directly, removing the need for accessing the mapping between the identifiers and the objects of interest, which simplifies the polling process.

**4. Memory Management and Result Handling**

In the Linux kernel, the responsibility of allocating memory for the structure that holds the results of the polling operation lies with the user. This requires additional checks within the kernel to ensure that the user has correctly allocated sufficient space, in addition to the need for copying the results back to user space. In contrast, the `Poller.poll()` is a user-level implementation, meaning it can handle the allocation of the required structure, abstracting away these concerns and simplifying the process for the user.

**5. Wake-Up Procedure**

The wake-up procedure employed by `Poller.poll()` is significantly simpler compared to the kernel's approach. The Linux kernel’s wake-up mechanism involves intricate interactions with thread states and scheduling, ensuring that the correct threads are woken up efficiently, while this user-level implementation's wake-up process is managed through the `ParkState` object, which, while effective, lacks the complexity and fine-grained control required at the kernel level.

**6. Adaptation to User-Level Environment**

The `Poller.poll()` implementation was specifically adapted for a user-level environment, in contrast to the kernel's lower-level implementation. When dealing with objects of interest that operate at the user level, having a polling mechanism aligned with that level is ideal. It helps to bypass the overhead and latency associated with system calls, leading to more efficient and responsive interactions.

## Adaptation of epoll()
### Algorithm Overview

Unlike `poll()`, the Linux Kernel's `epoll()` is implemented as a set of system calls, designed to handle high-performance event monitoring with minimal overhead. This performance is achieved through "active monitoring". In contrast to `poll()`, which requires iterating over the whole list of monitored objects each time a check for events is performed, `epoll()` uses an event-driven approach. Once an object is registered, the `epoll()` instance monitors it for events, allowing immediate responses to changes without requiring a scan over the entire list, which significantly reduces the overhead when dealing with a large number of monitored objects.

The adaptation of `epoll()` to a user-level environment, referred to as `Poller`, follows a similar design, though it does not encompass all the features provided by the `epoll()` instances, like the ability to nest `epoll()` instances without creating loops or deep wake-up paths. The `Poller` implementation comprises six key methods: `create()`, `add()`, `modify()`, `delete()`, `wait()`, and `close()`.

The following is an overview of how this adapted `epoll()` mechanism works:
1. *`create()`*
	Initializes a new instance of the event poller, setting up the internal structures necessary for monitoring the specified objects.

2. `add()`  
	Used to register a new object of interest along with the corresponding event mask that specifies which events should be monitored.
	
	The method follows these steps:
	1. Validates the object of interest and ensures that the provided event mask is valid. Certain flag combinations, like exclusivity with "one-shot," are checked for compatibility.
	2. Automatically includes the error (`POLLERR`) and hang-up (`POLLHUP`) flags in the event mask to ensure these events are always reported.
	3. Adds the object of interest to the poller instance's "interest list" after successful validation.
	4. Polls on the object, using a poll table initialized with a _poll queuing function_, serving not only the purpose of fetching available events, but also adding the poller instance as a waiter to be notified of future events.
		- The poller instance is added as an exclusive or non-exclusive waiter based on the presence of the exclusivity flag on the event mask.
		- The *wait queue function*, linked by the *poll queuing function* with the *poller*'s *wait queue entry*, is used by the monitored objects to add themselves to the "ready list" when the reported available events match the monitored events. It also triggers a wake-up call for the waiter of the poller instance.
	5. If any events are available during this registration process, the object is added to the "ready list," and a waiter is notified.

3. `modify()`
	This method is used to modify the event mask of a monitored object of interest or to re-arm it when the "one-shot" flag is used. It immediately polls on the object to check for available events. If events are found, the poller instance notifies a waiter to retrieve them.
	
	As previously mentioned in the "Poll Flags" section, neither the new event mask nor the current mask can have the exclusivity flag set when invoking this method, as doing so results in an error.

4. `delete()`
	Deletes an object of interest from the poller instance's "interest list," effectively stopping its monitoring. When an object is registered with the exclusivity flag, careful removal is necessary to avoid dismissing events that could have notified another exclusive waiter.

5.  `await()`
	This method waits for any events from the monitored objects to become available. It returns up to a specified maximum number (`maxEvents`) of objects with available events. If no events are immediately available, the method enters a loop, performing idle-waiting until one of the following conditions is met:
	- An interrupt signal is received.
	- The specified timeout is reached.
	- The thread is woken up, and events are available to be returned.

6. `close()`
	Invoking this method is similar to calling `delete()` on all objects in the "interest list." Additionally, it wakes up every waiter of the poller instance, allowing them to perceive the closure of the instance and return graciously.

### Comparison with the Linux Kernel's *epoll()* implementation

The user-level adaptation of the `epoll()` mechanism, implemented in the `Poller` class, draws inspiration from the Linux Kernel's `epoll()` system call but with several key differences due to the distinct environments in which they operate. Below is a comparison of the two implementations:

1. **User-level vs Kernel-level**
    - Similar to the `poll()` adaptation, the `Poller` class operates at the user-level, which reduces the complexity compared to the kernel-level `epoll()` implementation.
    
2. **Handling of Objects of Interest**
    - In the kernel, `epoll()` works with file descriptors, which are then used to retrieve the corresponding structures for monitoring. The user-level `Poller` directly receives instances of the objects of interest, bypassing the need for file descriptor-based lookups.
    - Additionally, the kernel’s `epoll()` must verify if a file descriptor supports polling, whereas in the user-level `Poller`, objects are forced to implement the `Pollable` interface, ensuring they are capable of being polled. This simplifies the verification process and eliminates the need for checking an object of interest. However, while objects implementing `Pollable` can always be polled, they may be closed, which demands a mechanism to indicate that *queuing* is no longer possible. In the `Poller`implementation, this information is conveyed when `Poller` instance's *poll queuing function* receives an uninitialized *wait queue entry* or when its *wait queue function* receives a combination of the `POLLFREE` and `POLLHUP` flags. 

3. **Memory Management and Result Handling**
    - The kernel implementation uses memory management techniques, such as caching frequently allocated objects, to enhance performance. In contrast, the user-level `Poller` is more straightforward, allocating and managing memory directly without relying on such caching mechanisms.
    - Additionally, while the kernel’s `epoll()` requires the user to allocate memory for result to be returned, the `Poller` instance handles this internally, simplifying the interface for users.

4. **Wake-Up Procedure**
    - The wake-up procedure in the `Poller` class is more straightforward than in the kernel's `epoll()`. As explained in the "Adaptation of poll()" section, the kernel must account for low-level scheduling and thread management, while the user-level `Poller` uses a simpler approach that combines `LockSupport` and `ParkState`.

5. **Adaptation to User-Level Environment**
    - The `Poller` class is specifically adapted to operate in a user-level environment, avoiding the overhead associated with system calls. This adaptation leads to improved performance when dealing with user-level objects, as it eliminates the need for costly kernel interactions.

6. **Creation and nesting of Poller Instances**
    - Unlike the kernel’s `epoll()`, the `Poller` instance does not support nesting of pollers. In the kernel, managing nested `epoll()` instances involves creating and managing associated file instances, verifying whether file descriptors being registered are `epoll()` instances or not, and acting accordingly, and ensuring that the wake-up paths do not form loops or become excessively deep. These complexities are avoided in the user-level `Poller`.

7. **Use of EPOLLWAKEUP and Wake-Up Sources**
    - The `Poller` implementation does not support the `EPOLLWAKEUP` flag or wake-up sources. In the kernel, `EPOLLWAKEUP` is used to prevent the system from entering sleep states while events are being monitored. This is particularly relevant in power management scenarios where keeping the CPU awake is crucial to handle critical events. Since the user-level `Poller` operates within a single process and does not interact with the system's power management, this feature is not necessary.

8. **File Descriptor Ownership**
    - In the kernel’s `epoll()`, the ownership of file descriptors is strictly checked, ensuring that only the process that owns a file descriptor can use it for monitoring. This security measure is not applicable in the user-level `Poller`, where objects of interest are directly provided and managed within the process, negating the need for ownership checks.

9. **File epoll hooks & Cleanup**
    - The user-level adaptation of the `epoll()` mechanism, does not implement the file hooks (such as the `->f_ep` list) used by the Linux kernel to track `epoll` instances monitoring a file descriptor. Instead, it relies on a simpler approach for cleanup purposes when an object of interest is closed or its monitoring is no longer needed. Specifically, the cleanup is invoked by waking up all waiters on the wait queue associated with the file descriptor and passing the `POLLFREE` flag in the key. This method ensures that all relevant waiters are notified to handle the cleanup and remove their *wait queue entry*, while not requiring a list of `epoll` instances to be used.

10. **Locking and Concurrency**
	<h1 style="color:red">"Not Finished"</h1>
    - The `Poller` class opts for a simplified concurrency model, utilizing a single reentrant lock mechanism to manage access to shared resources. In contrast, the kernel’s `epoll()` implementation may employ a combination of mutexes, read-write locks, and lockless operations to minimize contention and optimize performance. While this approach enhances scalability in the kernel, it introduces complexity that is not required in the user-level adaptation. However, as the `Poller` is developed further, incorporating more advanced concurrency controls may be necessary to improve performance in multi-threaded environments.
    - The kernel implementation involves synchronization between *CPUs* using memory barriers to ensure that changes are visible to all *CPUs*. This level of synchronization is unnecessary in the user-level `Poller`, as it operates within a single process space. As a result, the implementation is simplified, avoiding the complexity of ensuring visibility of changes across multiple processors.
	- with a single reentrant lock mechanism to manage concurrency and wake-up operations. The kernel implementation, on the other hand, may utilize a combination of mutexes, read-write locks, and lockless (atomic) operations to minimize contention and ensure efficient event reporting.




- all the reasons mentioned in the adaptation of poll(): 
	- Complexity and Low-Level Operations
		- synchronization between CPUs with use of memory barriers to ensure changes are visualized by all CPUs 
- locking & concurrency
	- Opted for using a single reentrant lock mechanism, instead of a combination of mutex, read-write-lock and lockless (atomic) operations. The combination of these locking mechanism and concurrent operations are not mandatory, but must be implemented in the future for performance purposes. Their main goal is to ensure the least contention possible when an object of interest wants to report events.

# Design Walk-through
- Falar no percurso até encontrar este mecanismo.
	- 1º tentei desenvolver um mecanismo destes com o intuito de servir apenas os links dos sockets, para se poder fazer `waitLink(sid : SocketIdentifier)` e `waitAnyLink() : Socket Identifier`. Na primeira tentativa pensei em criar queue's de pedidos para cada link, e em registar-me em todas as queues, no entanto, surgiram alguns problemas que me levaram a desistir dessa abordagem. O primeiro era não saber como acordar uma thread registada em múltiplos locais sem ser através da criação de uma condição (de lock) criada especialmente para a thread a manifestar interesse em algum link. A criação destas condições a cada pedido não me pareceu razoável, logo ponderei a possibilidade de na API se poder "registar" a thread, ficando uma condição associada e que mais tarde poderia ser eliminada quando o utilizador decidisse que a thread não seria mais utilizada para esse propósito. Para além desse problema, pensei que quando fosse demonstrado o interesse em todos os links (`waitAnyLink()`), registar a thread em todas as queues seria muito pouco eficiente, além de ser necessário posteriormente remover todos esses registos após receber a resposta. Dados esses problemas, e não me apercebendo da utilidade de esperar por uma combinação específica de links para o desenvolvimento de sockets, que por consequência indica uma baixa probabilidade de alguém usar tal funcionalidade, decidi optar por uma outra abordagem.
	- Falar da abordagem que desenvolvi por inteiro, como uma queue global para pedidos que tivessem interesse em todos os links. Falar também que era um pouco rígido demais, consumia créditos para garantir reservas, etc.
	- Depois, surgindo a necessidade de um poller, aqui não se poderia escapar à demonstração de interesse em combinações distintas de elementos, logo decidi explorar os mecanismos mais conhecidos para estas tarefas: select(), poll() e epoll(). De alguma forma tenho de incluir o que descobri sobre estes, e as minhas conclusões sobre as suas utilidades.
	- Falar então que decidi implementar uma versão mais simples do epoll(), que não permite "nesting", e que essencialmente tem como base a ideia que tive inicialmente e descartei. Sendo esta uma versão bem mais sofisticada para além de ser genérica e muito menos rígida que a minha. O formato de registar callbacks que até lá me pareceu perigoso, passou a parecer-me extremamente útil desde que o acesso fosse reservado a partes internas do middleware já que estas, se forem bem testadas não exibirão comportamento malicioso e contraprodutivo. O facto de o interesse ser registado e mantido, até ser explicitamente eliminado, seja pelo callback de espera ou quando já não houver interesse, resolveu uma das minhas preocupações que era a cada chamada registar o interesse em cada pedido só para em breve o ter de remover. A possibilidade de usar em modo level-trigger ou edge-trigger, poder registar-se o interesse como exclusivo ou não, e ainda poder remover o interesse momentaneamente (EPOLLONESHOT) fazem este mecanismo bastante desejável.
	- Relativamente ao problema referido inicialmente de não conhecer uma solução para além de criar uma condição, decidi optar por uma solução que não escapa da criação de um objeto, chama-se ParkState e inclui a referência da thread associada a este e uma variável booleana atómica para determinar quando a thread está à espera e quando é acordada. Apesar de exigir a criação de um objeto, ao combinar o facto de poder manter o registo do interesse, logo não sendo necessário criar o objeto múltiplas vezes, com o facto de não exigir a existência e a aquisição de um lock, esta solução pareceu-me satisfatória. Para uma thread esperar e posteriormente ser acordada, recorri ao mecanismo LockSupport que vim a descobrir posteriormente.
	- Falar de como é evitado o uso de mecanismos de sincronizacao como Locks+Condicao para acordar as threads e usa-se LockSupport que permite acordar a thread específica que pretendemos. Usa-se o park state e define-se preventivamente a thread como "parked". Esta atribuição é colocada em todos os momentos na qual se pretende que a entrada seja dada como válida para ser acordada, mesmo que a seguir se sigam múltiplas ações antes de ser esperar ("dormir" até ser sinalizado ou ficar sem tempo, i.e., *times out*). 
		- Por exemplo, um waiter de um poller marca o seu estado como "parked" antes de adicionar a sua entrada na queue de waiters do poller. Isto porque a partir do momento em que a entrada é adicionada e o lock seja libertado (após a adição) é possível que o poller queira acordar a thread, mas se ela não estiver marcada como "estacionada" então não o vai ser, podendo outra thread vir a ser sinalizada no seu lugar, ou até mesmo ser necessário esperar por um próximo evento para tal acontecer. 
		- Outro exemplo, é no poll imediato (sem instancia de poller) em que se define o estado de "estacionamento" como "parked" antes de se começar a tentar pescar eventos. Isto é necessário porque é possível que após passar por um pollable que não mostra qualquer evento como disponível, que este passe a ter eventos disponíveis. Se o estado não estiver receptível a wake-up calls, então se não for encontrado qualquer evento nos restantes pollables, a thread irá "esperar" desnecessariamente por uma nova notificação quando uma notificação tinha sido emitida anteriormente mas não foi aceitada porque o estado não estava receptível a tal, i.e., não estava como "parked".
	- 
- tentei pesquisar por implementações em Java que simulassem o epoll. Como é o caso do Selector que permite este tipo de funcionalidade. No entanto, este mecanismo assim como as system calls, utiliza por base descritores de ficheiros, e então acede ao kernel. As trocas entre user e kernel são custosas. Logo pareceu-me ineficiente optar por um "workaround" em que criava descritores de ficheiros associados a sockets virtuais (que existem apenas no espaço do utilizado) só para reaproveitar um mecanismo de polling. A utilização desse mecanismo, de certa forma, removeria a vantagem de multiplexar todas as mensagens sobre o mesmo socket UDP. Dito isto, optei por estudar a implementação de um mecanismo open-source que seguramente seria boa já que está disponível numa grande quantidade de dispositivos, nos dispositivos que têm por base linux. Este estudo permitiria-me entender como especialistas da área abordaram este problema, e desta forma seria-me possível fazer uma adaptação do mecanismo para Java para ser utilizado unicamente no nível de utilizador (sem recorrer ao kernel) e desta forma conseguir obter um desempenho superior por não serem necessárias trocas com o kernel mas também que não necessitaria de consumir recursos desnecessários com a criação de pipes (ou outro tipo de ficheiros) para permitir reaproveitar um mecanismo já desenvolvido.
	- Aqui em baixo pode ter conteudo que ajuda a escrever o walk-through
# Notas
- Destacar o mecanismo das wait queues que será amplamente utilizado.
- O poller será utilizado não só pelo utilizador para esperar por eventos nos sockets de interesse, mas também a nível interno para fornecer funcionalidades de esperar pela disponibilidade de um link qualquer. 




# Arquitetura
## Operação de interesse
Um poller não é utilizado para registar sockets de interesse, mas para registar a vontade de realizar um certo tipo de operação sobre um socket específico.
O tipo de operações de interesse que serão suportados são: leitura (*IN*) e escrita (*OUT*).
## Poller
### Variáveis
- Lock + condição
	- **Rationale:** Para evitar espera ativa enquanto não existe um socket disponível para realizar uma operação de interesse.
- Map de etiquetas (dos sockets) para objeto sobre o socket
	- O objeto deve conter:
		- tipo de operação de interesse a realizar sobre o socket;
		- flag que indica se o socket está disponível para realizar a operação;
### Interfaces
#### Poller
- `static +createPoller() : Poller` - cria poller
- `+register(s : Socket, op : PollOperation)` - regista socket no poller
	- "op" pode ser:
		- Poller.**POLLIN** para leitura
		- Poller.**POLLOUT** para escrita
- `+unregister(s : Socket)` - remove socket do poller
- `+poll()` - 
## Socket
### Variáveis
- 

# Lógica
## Problemas
- **Lançar exceção** quando se tenta subscrever uma operação que o socket não consegue realizar.
- Pollers não devem ser thread-safe, até porque não faz sentido mais do que uma thread utilizar o mesmo poller.
- Sockets thread-safe + possibilidade de incluir o mesmo socket em vários pollers
	- Significa que múltiplos pollers e até threads que não utilizem um poller possam estar interessados na mesma operação para o mesmo socket. Logo, como é que consegue acordar threads ou pollers de forma inteligente para que não se estorvem uns aos outros?  
		- Imaginando que dois pollers estão interessados em receber uma mensagem de um socket específico. Se apenas existir uma mensagem para receber e se ambos os pollers forem avisados que o socket está disponível para receber, então o uso do método `receive()` bloqueante pelas threads dos pollers resultará no bloqueio de uma das thread.
		- A solução deve passar por fazer o utilizador assumir a responsabilidade de querer seguir uma programação absurda. Se apenas tiver uma thread interessada em realizar o tipo de operação sobre o socket então não existirá problemas. Pode esperar que o poller informe que o socket está disponível para realizar a operação e utilizar o método bloqueante dessa operação sem que resulte no bloqueio da thread. Se optar por ter múltiplas threads a realizar a mesma operação, dentro das quais algumas utilizam um poller para verificar a disponibilidade da operação, então correm o risco das threads competirem pela execução da operação, e no caso de ser utilizado o método bloqueante, resultar no bloqueio de múltiplas threads. Portanto, deve optar por utilizar o método não bloqueante que tenta realizar a operação, e retorna "falha" se a operação devesse bloquear. 
- Adicionar respostas discutidas no email com o Professor.
- Reuniao 6 Maio - Ideias sobre pollers 
	![[Reuniao 2024-05-06#Selectors / Pollers]]
- 