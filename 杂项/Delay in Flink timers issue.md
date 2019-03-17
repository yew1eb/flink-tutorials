Checkpoints are largely asynchronous, but the checkpointing of timers has
some synchronous component (which we are currently working on getting rid
of).

So when you have a lot of timers, streams stall for a short time while the
timers are checkpointed. If all goes as planned, Flink 1.6 will not have
that stall any more.

Concerning the delay on timers - I think that is not an issue of heaps /
timer wheels, etc (timer wheels are not magically better at everything that
has to do with timers).
This sounds more like the execution becomes contended. The reason for the
contention could actually very well be the checkpointing of timers
(stalling when too many timers are registered).