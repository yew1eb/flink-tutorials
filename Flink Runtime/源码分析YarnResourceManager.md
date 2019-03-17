/**

 * The yarn implementation of the resource manager. Used when the system is started
 * via the resource framework YARN.
    */
    public class YarnResourceManager extends ResourceManager<YarnWorkerNode> implements AMRMClientAsync.CallbackHandler {

    /** The process environment variables. */
    private final Map<String, String> env;

    /** YARN container map. Package private for unit test purposes. */
    private final ConcurrentMap<ResourceID, YarnWorkerNode> workerNodeMap;

    /*YarnResourceManager* The heartbeat interval while the resource master is waiting for containers. */
    private static final int FAST_YARN_HEARTBEAT_INTERVAL_MS = 500;

    /** Environment variable name of the final container id used by the YarnResourceManager.
     * Container ID generation may vary across Hadoop versions. */
      private static final String ENV_FLINK_CONTAINER_ID = "_FLINK_CONTAINER_ID";

    /** Environment variable name of the hostname given by the YARN.
     * In task executor we use the hostnames given by YARN consistently throughout akka */
      static final String ENV_FLINK_NODE_ID = "_FLINK_NODE_ID";

    /** Default heartbeat interval between this resource manager and the YARN ResourceManager. */
    private final int yarnHeartbeatIntervalMillis;

    private final Configuration flinkConfig;

    private final YarnConfiguration yarnConfig;

    @Nullable
    private final String webInterfaceUrl;

    private final int numberOfTaskSlots;

    private final int defaultTaskManagerMemoryMB;

    private final int defaultCpus;

    /** Client to communicate with the Resource Manager (YARN's master). */
    private AMRMClientAsync<AMRMClient.ContainerRequest> resourceManagerClient;

    /** Client to communicate with the Node manager and launch TaskExecutor processes. */
    private NMClient nodeManagerClient;

    /** The number of containers requested, but not yet granted. */
    private int numPendingContainerRequests;

    private final Map<ResourceProfile, Integer> resourcePriorities = new HashMap<>();
