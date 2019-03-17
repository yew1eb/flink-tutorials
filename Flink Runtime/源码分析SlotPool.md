/**

 * The slot pool serves slot request issued by {@link ExecutionGraph}. It will will attempt to acquire new slots
 * from the ResourceManager when it cannot serve a slot request. If no ResourceManager is currently available,
 * or it gets a decline from the ResourceManager, or a request times out, it fails the slot request. The slot pool also
 * holds all the slots that were offered to it and accepted, and can thus provides registered free slots even if the
 * ResourceManager is down. The slots will only be released when they are useless, e.g. when the job is fully running
 * but we still have some free slots.
    *
 * <p>All the allocation or the slot offering will be identified by self generated AllocationID, we will use it to
 * eliminate ambiguities.
    *
 * <p>TODO : Make pending requests location preference aware
 * TODO : Make pass location preferences to ResourceManager when sending a slot request
    */
    public class SlotPool extends RpcEndpoint implements SlotPoolGateway, AllocatedSlotActions {

    /** The interval (in milliseconds) in which the SlotPool writes its slot distribution on debug level. */
    private static final int STATUS_LOG_INTERVAL_MS = 60_000;

    private final JobID jobId;

    private final SchedulingStrategy schedulingStrategy;

    private final ProviderAndOwner providerAndOwner;

    /** All registered TaskManagers, slots will be accepted and used only if the resource is registered. */
    private final HashSet<ResourceID> registeredTaskManagers;

    /** The book-keeping of all allocated slots. */
    private final AllocatedSlots allocatedSlots;

    /** The book-keeping of all available slots. */
    private final AvailableSlots availableSlots;

    /** All pending requests waiting for slots. */
    private final DualKeyMap<SlotRequestId, AllocationID, PendingRequest> pendingRequests;

    /** The requests that are waiting for the resource manager to be connected. */
    private final HashMap<SlotRequestId, PendingRequest> waitingForResourceManager;

    /** Timeout for external request calls (e.g. to the ResourceManager or the TaskExecutor). */
    private final Time rpcTimeout;

    /** Timeout for releasing idle slots. */
    private final Time idleSlotTimeout;

    private final Clock clock;

    /** Managers for the different slot sharing groups. */
    protected final Map<SlotSharingGroupId, SlotSharingManager> slotSharingManagers;

    /** the fencing token of the job manager. */
    private JobMasterId jobMasterId;

    /** The gateway to communicate with resource manager. */
    private ResourceManagerGateway resourceManagerGateway;

    private String jobManagerAddress;