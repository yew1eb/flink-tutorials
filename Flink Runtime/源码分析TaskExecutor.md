	public static final String TASK_MANAGER_NAME = "taskmanager";
	
	/** The access to the leader election and retrieval services. */
	private final HighAvailabilityServices haServices;
	
	private final TaskManagerServices taskExecutorServices;
	
	/** The task manager configuration. */
	private final TaskManagerConfiguration taskManagerConfiguration;
	
	/** The heartbeat manager for job manager in the task manager. */
	private final HeartbeatManager<Void, AccumulatorReport> jobManagerHeartbeatManager;
	
	/** The heartbeat manager for resource manager in the task manager. */
	private final HeartbeatManager<Void, SlotReport> resourceManagerHeartbeatManager;
	
	/** The fatal error handler to use in case of a fatal error. */
	private final FatalErrorHandler fatalErrorHandler;
	
	private final BlobCacheService blobCacheService;
	
	// --------- TaskManager services --------
	
	/** The connection information of this task manager. */
	private final TaskManagerLocation taskManagerLocation;
	
	private final TaskManagerMetricGroup taskManagerMetricGroup;
	
	/** The state manager for this task, providing state managers per slot. */
	private final TaskExecutorLocalStateStoresManager localStateStoresManager;
	
	/** The network component in the task manager. */
	private final NetworkEnvironment networkEnvironment;
	
	// --------- job manager connections -----------
	
	private final Map<ResourceID, JobManagerConnection> jobManagerConnections;
	
	// --------- task slot allocation table -----------
	
	private final TaskSlotTable taskSlotTable;
	
	private final JobManagerTable jobManagerTable;
	
	private final JobLeaderService jobLeaderService;
	
	private final LeaderRetrievalService resourceManagerLeaderRetriever;
	
	// ------------------------------------------------------------------------
	
	private final HardwareDescription hardwareDescription;
	
	private FileCache fileCache;
	
	// --------- resource manager --------
	
	@Nullable
	private ResourceManagerAddress resourceManagerAddress;
	
	@Nullable
	private EstablishedResourceManagerConnection establishedResourceManagerConnection;
	
	@Nullable
	private TaskExecutorToResourceManagerConnection resourceManagerConnection;
	
	@Nullable
	private UUID currentRegistrationTimeoutId;


### TaskManagerServices

http://chenyuzhao.me/2017/02/09/taskmanager%E5%9F%BA%E6%9C%AC%E7%BB%84%E4%BB%B6/



### NetworkEnvironment

