
ML:<http://apache-flink-mailing-list-archive.1008284.n3.nabble.com/PROPOSAL-Improving-Flink-s-timer-management-for-large-state-td22491.html#a22534>
design doc: <https://docs.google.com/document/d/1XbhJRbig5c5Ftd77d0mKND1bePyTC26Pz04EvxdA7Jc/edit#heading=h.17v0k3363r6q>


[PROPOSAL] Improving Flink’s timer management for large state.

Stefan Richter (srichter@data-artisans.com)

## Motivation（动机）
Timers are a special form of state in Flink that can be logically be categorized as keyed state because all timers are scoped by a key (and a namespace, e.g. a window; see class InternalTimer). 
For historical reasons, timers are managed separately from all other keyed state: other keyed state is managed by instances of KeyedStateBackend, 
whereas timers are managed by instances of InternalTimerService. 

Right now, Flink offers HeapInternalTimerService as the only implementation of InternalTimerService. This implementation is memory-bound because it keeps all timer state on the heap. 
For large amounts of timers, this can become problematic because the job can run out of memory. In general, Flink offers an implementation for KeyedStateBackend that is based on RocksDB to support keyed state that can grow larger than the available main memory. 
However, this choice of keyed state backend has currently no effect on the timer service. 
Flink always uses the only available, heap-based implementation HeapInternalTimerService for timers. 
If we want to support large timer state, we should offer an implementation of InternalTimerService that is based on RocksDB.

Another shortcoming of the current implementation of timer state is the integration with checkpointing. 
All keyed state backends in Flink support asynchronous save/checkpoints but timer state snapshots are still synchronous. 
This means that the processing pipeline is stalling while Flink is persisting the timer state and is particularly bad if we consider that persistence involves communication to external systems (such as HDFS, S3, …) that can block for an unknown amount of time.
From a code maintenance point of view, this is also not optimal because we are maintaining separate code paths for snapshotting and restoring timers. 
This code has have many similarities with how other keyed state is snapshotted (separation into key-groups to support rescaling, persistence of metadata and serializers, iteration and serialization state elements; and the inverse for restore). Another disadvantage that is caused by this is that timer state also goes into a different snapshot file that has to be managed. The implementation currently exploits and blocks the raw keyed state for this, which was originally intended to be exposed for users as a way to snapshot some custom keyed state that exists outside the keyed state backends.

The purpose of this improvement proposal is to consolidate timer state. We can leverage the fact that timer state has the same basic properties as other keyed state to integrate the management of timer state into the keyed state backend, where it should belong. In summary, our goals are:
Have an implementation of timer services that operates on RocksDB.
Support asynchronous snapshots for all timer state.
Support incremental snapshots for timer state in RocksDB.
Integrate timer state as another form of keyed state in keyed state backends in a way that leverages the existing snapshotting code to eliminate special casing code paths that do similar things. As as nice side effect, this would also free the raw keyed state for user state. 
Approach
Integrating timers as keyed state in keyed state backends.

The basic idea of integrating  InternalTimerService with KeyedStateBackend is based on the observation that timers can mostly be seen as just another form of keyed state. 

Interface

On the interface level, we can simply mimic the process of registering other keyed state for timer state by introducing a method 

InternalTimerService getOrCreateTimerService(
String serviceName, 
TypeSerializer<N> namespaceSerializer) throws Exception;

in the KeyedStateBackend interface. 

This method is then invoked by InternalTimeServiceManager::getInternalTimerService.

The effect of this method in the backend is that the underlying data structures for the  InternalTimerService can be registered and allocated in the backend. This is again similar to what happens when we register other keyed state. As a registered structure, the state can participate in the backend’s snapshotting procedure.

Timer state in the HeapKeyedStateBackend
Timer state requires a data structure that offers reasonable fast inserts (with deduplication), removes, and in-order iteration. Our state table implementations in the HeapKeyedStateBackend are based on hash maps to offer fast key-value mappings, but are unordered. This means they are not well suited as a data structure for timers. We have implemented a suitable data structure based on a priority queue + hash set called InternalTimerHeap in FLINK-9423 (corresponding PR #6062, see https://github.com/StefanRRichter/flink/blob/fffc7ff6750509de2c97ea7ed44d2404669acd35/flink-streaming-java/src/main/java/org/apache/flink/streaming/api/operators/InternalTimerHeap.java).  We can introduce a mapping from String -> InternalTimerHeap, similar to the field HeapKeyedStateBackend.stateTables to track registered timer state. As  InternalTimerService currently holds state for processing-time timers and event-time timers, it would register two InternalTimerHeap with the backend on when the timer service is created.

Notice that the data structure is also suited to implement asynchronous snapshots on top of it. For example, we can take a shallow copy (timer objects are immutable for the purpose of writing them to a snapshot) of the array that holds the priority queue in the synchronous part. In the asynchronous part, we could partition the data into key-groups (similar to how we do it for the snapshots of CopyOnWriteStateTable) , and write them to stable storage. When we push the key-group partitioning into the asynchronous part of the checkpoint, we can also stop separating the deduplication maps in InternalTimerHeap by key-groups and unify them into one.

In general, we can easily integrate snapshotting of timers with the snapshotting of all other keyed state, i.e. iterating the timers to write them when their corresponding key-groups in the backend are written.
Timer state in the RocksDBKeyedStateBackend
RocksDB is a key-value store based on LSM-trees and keeps data already in key-order. This means that it can support all required operations for a timer state out-of-the-box. We need to take key-grouping into consideration, which breaks up the sort order of the timers, because they must be partitioned into key groups. However, we could use a “heap of time-sorted key-groups” to implement timer state based on RocksDB. We can do an implementation that is loosely based on some ideas from the class RocksDBTimerHeap in PR #3359 (https://github.com/alibaba/flink/blob/88916fe76f20467a43ff3cf6b8f86b8d36bc9e23/flink-timer-services/flink-timerservice-rocksdb/src/main/java/org/apache/flink/contrib/streaming/timerservice/RocksDBTimerHeap.java). 
With such an approach, all timers stored in RocksDB as normal keyed state and we can also use features like asynchronous and incremental snapshots out-of-the-box.
Migration
With the presented changes, timer state will be automatically snapshotted with the keyed state backend. This means that the current dedicated path for snapshot and restore of timer services becomes obsolete via the raw keyed state. Those changes mean that we need to uptick the savepoint version and must provide backwards compatibility with old savepoints.

The migration path would be fairly straight forward. Unlike with the new savepoints, which will restore the state right into the keyed state backends, we need to detect old savepoints by they savepoint version. Then, we need to open and access the raw keyed state files of the savepoint (using the parts of the old restore code), iterate the timers, and insert them into the new timer structures in the backend. 
This procedure is established and has successfully worked to provide backwards compatibility in previous changes that we introduced to the savepoint format.
Further ideas
Separate InternalTimerService into two parts for processing and event time.
Currently, InternalTimerSevice holds two timer states (processing timers and event timers) and offers corresponding methods for both time scales. In our outlined improvement approach, one InternalTimerSevice would probably register two timer states in the keyed state backend. We can consider to break up internal timer services in two when we abstract over the common methods to manage timers and the two distinctive methods advanceWatermark(long) and onProcessingTime(long), which might also be unified to something like reportTime(long).

This would give more fine-grained control over which type of timer state is registered and a better separation of concerns.

Implementation steps
See: https://issues.apache.org/jira/browse/FLINK-9485
