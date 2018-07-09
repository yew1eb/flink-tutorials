## 前言
新的Flink On Yarn架构FLIP6将在flink 1.5版本释出
下文主要是分析FLIP6新的Flink-on-Yarn架构。

## FLIP6 Flink-on-YARN
Compared to the state in Flink 1.1, the new Flink-on-YARN architecture offers the following benefits:

+ The client directly starts the Job in YARN, rather than bootstrapping a cluster and after that submitting the job to that cluster. The client can hence disconnect immediately after the job was submitted

+ All user code libraries and config files are directly in the Application Classpath, rather than in the dynamic user code class loader

+ Containers are requested as needed and will be released when not used any more

+ The “as needed” allocation of containers allows for different profiles of containers (CPU / memory) to be used for different operators

## 下面是Till Rohrmann对FLIP6的一些说明
Flip-6 is intended to solve some of the Flink's shortcomings with respect to resource management and improve its deployment flexibility. We have seen in the past that Flink's legacy abstraction was not well suited to support an ever increasing set of different deployments. 

Flink started with the standalone mode which runs Flink on a bare-metal cluster. Soon it became evident that people would like to run Flink on top of cluster resource managers such as Yarn or Mesos. Consequently, support for Yarn was added. Until then, there was only a single execution mode which is now called the session mode. The session mode allows you to run multiple jobs on the same Flink cluster at the cost of no resource isolation. With Yarn we added a new per-job mode which starts a Flink cluster for each job and gives you resource isolation. The next step was the integration with Mesos and now many people want to run Flink in a containerized environment (Docker and Kubernetes). 

On top of that, a much sought after feature since quite some time is that Flink should be able to dynamically allocate more resources in order to scale jobs up and release resources if they are not used to capacity. That way one won't waste resources or under provision the Flink cluster if facing changing workloads.

Since Flink has grown over time and some of the requirements weren't clear from the very beginning, it seemed quite difficult to make Flink work in all the different settings with support for dynamic scaling. So in order to make Flink future proof deployment-wise and adding support for full resource elasticity the community started the Flip-6 effort.

Flip-6 split the existing architecture up into 4 components: JobMaster, TaskExecutor, ResourceManager and Dispatcher. The JobMaster is now responsible for running a single job. The TaskExecutor remained more or less the same and is responsible for executing tasks which the JobMaster deploys to it. The ResourceManager is the integration component with an external system like Yarn and Mesos. Its task is to allocate new containers/tasks to spawn new TaskExecutors if need be. The Dispatcher is the component responsible for receiving new jobs and spawning a new JobMaster to execute them.

The idea now is to use these building blocks to implement the session as well as the per-job mode in the different deployment scenarios.

Flink 1.5 will run per default on the new Flip-6 architecture and supports Yarn, Mesos as well as the standalone mode. Thus, it supports the same deployment mode which Flink 1.4 supported. Additionally, it should now be easier to run Flink in a containerized environment since the client now communicates via REST calls with the Flink cluster. On Yarn and Mesos it will also allow to dynamically allocate and free resources which enables rescaling of jobs.

The next logical step would be to provide a better K8 integration which allows K8 to add and remove pods which are then automatically used by Flink.

For more information you can take a look at [1] which gives an overview about the architecture or simply reach out to me.


![image.png](https://upload-images.jianshu.io/upload_images/11601528-e6ac100516b23be7.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

从上图，我们可以看出实际上是在Client与 ResourceManager之间增加了一个代理的服务Flink Dispatcher， 实际上就是为了解耦。

## 启动一个Flink YARN Session
官方文档上提供了两种方式去部署flink作业到yarn上运行，由于本文不讨论Flink作业任务的调度部分，仅仅是探究flink的JM/TM节点是怎么从yarn集群上起来的，所以接下来我会深入分析“Start Flink Session”中，执行eg.  ``./bin/yarn-session.sh -qu root.rt.flink-queue -n 4 -tm 8192 -s 4`` 脚本背后发生的事情。

`yarn-session.sh`脚本的详细的参数说明见<https://ci.apache.org/projects/flink/flink-docs-master/ops/deployment/yarn_setup.html#start-a-session>

```
题外话：
在企业的生产环境中,一般会加一些安全的身份认证机制，如kerberos，在通过yarn client访问yarn集群之前。在提交flink job作业的机器，需要安装keytab。
//TODO kerberos具体是个啥？
```

## 深入源码中去
### CliFrontend
CliFrontend.main:
-> CliFrontend cli = new CliFrontend(configuration, customCommandLines);
-> cli.parseParameters(args)
--> run() -> runProgram方法的主要逻辑如下

```java
/*
Create a {@link ClusterDescriptor} from the given configuration, 
configuration directory and the command line.
*/
final ClusterDescriptor<T> clusterDescriptor = customCommandLine.createClusterDescriptor(commandLine);

/*Returns the cluster id if a cluster id was specified on the command line, otherwise it returns null.
A cluster id identifies a running cluster, 
e.g. the Yarn application id for a Flink cluster running on Yarn. */
final T clusterId = customCommandLine.getClusterId(commandLine);

final ClusterClient<T> client;

// directly deploy the job if the cluster is started in job mode and detached
if (isNewMode && clusterId == null && runOptions.getDetachedMode()) {
	int parallelism = runOptions.getParallelism() == -1 ? defaultParallelism : runOptions.getParallelism();
/*
Creates a {@link JobGraph} from the given {@link PackagedProgram}.
*/
	final JobGraph jobGraph = PackagedProgramUtils.createJobGraph(program, configuration, parallelism);

	final ClusterSpecification clusterSpecification = customCommandLine.getClusterSpecification(commandLine);
/*
Deploys a per-job cluster with the given job on the cluster.
*/
	client = clusterDescriptor.deployJobCluster(
		clusterSpecification,
		jobGraph,
		runOptions.getDetachedMode());

```
简单梳理一下上面代码的主要流程：

1. create a YarnClusterDescriptor:   `Implementation of {@link AbstractYarnClusterDescriptor} which is used to start the application master.; FlinkYarnSessionCli.createClusterDescriptor(CommandLine)`
2. create a JobGraph: `PackagedProgramUtils.createJobGraph(program, configuration, parallelism)`
3. Deploys a per-job cluster with the given job on the cluster.`clusterDescriptor.deployJobCluster(clusterSpecification,jobGraph,runOptions.getDetachedMode())`

#### YarnClusterDescriptor extends AbstractYarnClusterDescriptor

```java
	private final YarnConfiguration yarnConfiguration;
	private final YarnClient yarnClient;
	/** True if the descriptor must not shut down the YarnClient. */
	private final boolean sharedYarnClient;
	private String yarnQueue;
	private String configurationDirectory;
	private Path flinkJarPath; // 用户jar路径
	private String dynamicPropertiesEncoded;
	/** Lazily initialized list of files to ship. */
	protected List<File> shipFiles = new LinkedList<>(); // 传输指定目录里面的文件
	private final Configuration flinkConfiguration;
	private boolean detached;
	private String customName;
	private String zookeeperNamespace;
	/** Optional Jar file to include in the system class loader of all application nodes
	 * (for per-job submission). */
	private final Set<File> userJarFiles = new HashSet<>();
```
#### PackagedProgramUtils.createJobGraph
//TODO StreamGraph -> JobGraph ，不在本文中进行分析，会单独写一篇文章进行分析。

#### YarnClusterDescriptor.deployJobCluster(clusterSpecification,jobGraph,detached)
方法里面实际是调用这个`deployInternal(clusterSpecification,"Flink per-job cluster",getYarnJobClusterEntrypoint(),jobGraph,detached)`方法。
getYarnJobClusterEntrypoint()返回 YarnJobClusterEntrypoint类的类全限定名称。
主要流程如下：
1. Check if configuration is valid
2. Check if the specified queue exists
3. Add dynamic properties to local flinkConfiguraton
4. Check if the YARN ClusterClient has the requested resources
    1. Create application via yarnClient； 
    2. validateClusterResources主要是检查Yarn的NodeManagers上有没有足够的内存来启动JMs/TMs.
5. start an application master
6. update  flinkConfiguration's `jobmanager.rpc.address|port , rest.address|port`

#### 启动 application master节点

```
ApplicationReport report = startAppMaster(
	flinkConfiguration,
	applicationName,
	yarnClusterEntrypoint,
	jobGraph,
	yarnClient,
	yarnApplication,
	clusterSpecification);
```

跟进到startAppMaster方法内部，这个方法非常重要，也非常长,依次分析一下它都做了什么：

1. Initialize the file systems(HDFS)，and 添加需要上传的文件(eg. 日志配置文件、用户 jar、flink-conf.yaml， lib jars, 指定的shipFiles)
2. Add Zookeeper namespace to local flinkConfiguraton
3. add the user code jars from the provided JobGraph
4. 整理系统lib jars 与user jar的classpath顺序
5. create remotePathJar "flink.jar", <dst_hdfs_path, local_paths>
6. create remotePathConf "flink-conf.yaml", <dst_hdfs_path, local_paths>
7. create jobGraph file "job.graph" <dst_hdfs_path, local_temp_file>
8. create remoteKrb5Path
9. create remoteYarnSiteXmlPath
10. create remotePathKeytab
11. Prepare Application Master Container, `ContainerLaunchContext amContainer = 
 setupApplicationMasterContainer(yarnClusterEntrypoint,hasLogback,hasLog4j,hasKrb5,clusterSpecification.getMasterMemoryMB())`
     1. respect custom JVM options in the YAML file (javaOpts = FLINK_JVM_OPTIONS + FLINK_JM_JVM_OPTIONS)
      2. `org.apache.flink.yarn.Utils.calculateHeapSize` //TODO
      3. 配置appication maste的启动command命令，包含以下内容："%java% %jvmmem% %jvmopts% %logging% %class% %args% %redirects%" ， 其中启动类会被指定为yarnClusterEntrypoint。
12. set HDFS delegation tokens when security is enabled
 `Utils.setTokensFor(amContainer, paths, yarnConfiguration);`
13. Set `localResources` required by the amContainer. 
14. Setup CLASSPATH and environment variables for ApplicationMaster
15. set user specified app master environment variables `containerized.master.env.` in the flink-conf.yaml
16. set Flink app class path `classPathBuilder`
17. set Flink on YARN internal configuration values as environment variables used  for settings of the containers, 
 see details `org.apache.flink.yarn.YarnConfigKeys`
18. set classpath from YARN configuration, see `Utils.setupYarnClassPath(yarnConfiguration, appMasterEnv)` add `DEFAULT_YARN_APPLICATION_CLASSPATH`, like `/share/hadoop/common/*`.
19. Set up resource type requirements for ApplicationMaster(memory, vcore)
20. set  appname,type,ContainerLaunchContext, Resorce yarnqueue, yaran.tags for  ApplicationSubmissionContext `appContext`
21. add a hook to clean up in case deployment fails `Runtime.getRuntime().addShutdownHook(DeploymentFailureHook)`
22. Submitting application master `yarnClient.submitApplication(appContext)`
23. Waiting for the cluster to be allocated, until the Application State s running, then deployed successfully.
24. since deployment was successful, remove the deploymentFailureHook
25. return `ApplicationReport report = yarnClient.getApplicationReport(appId)`


###  YarnJobClusterEntrypoint
咱们略过`yarnClient.submitApplication` yarn的调度细节， 直接看到YarnJobClusterEntrypoint ——启动Yarn Application Master进程的启动类, 从main往下找.

```java
# ClusterEntrypoint.runCluster
	protected void runCluster(Configuration configuration) throws Exception {
		synchronized (lock) {
			initializeServices(configuration);

			// write host information into configuration
			configuration.setString(JobManagerOptions.ADDRESS, commonRpcService.getAddress());
			configuration.setInteger(JobManagerOptions.PORT, commonRpcService.getPort());

			startClusterComponents(
				configuration,
				commonRpcService,
				haServices,
				blobServer,
				heartbeatServices,
				metricRegistry);

			dispatcher.getTerminationFuture().whenComplete(
				(Void value, Throwable throwable) -> {
					if (throwable != null) {
						LOG.info("Could not properly terminate the Dispatcher.", throwable);
					}

					// This is the general shutdown path. If a separate more specific shutdown was
					// already triggered, this will do nothing
					shutDownAndTerminate(
						SUCCESS_RETURN_CODE,
						ApplicationStatus.SUCCEEDED,
						throwable != null ? throwable.getMessage() : null,
						true);
				});
		}
	}
```
拆分以下上面代码的主要流程：
1. initializeServices
      1. create an AkkaRpcService `commonRpcService = createRpcService(configuration, bindAddress, portRange);`
     2. update jobmanager.rpc.address|port use commonRpcService in flink configuration.
     3. haServices = createHaServices(configuration, commonRpcService.getExecutor())
     4. blobServer = new BlobServer(configuration, haServices.createBlobStore());
     5. heartbeatServices = createHeartbeatServices(configuration);
     6. metricRegistry = createMetricRegistry(configuration)
     7. MetricQueryService
     8. archivedExecutionGraphStore = createSerializableExecutionGraphStore(configuration, commonRpcService.getScheduledExecutor())
     9. clusterInformation = new ClusterInformation(
				commonRpcService.getAddress(),
				blobServer.getPort());
     10.  transientBlobCache = new TransientBlobCache(configuration,new InetSocketAddress(clusterInformation.getBlobServerHostname(),clusterInformation.getBlobServerPort()));

2. startClusterComponents(configuration,commonRpcService,haServices,blobServer,heartbeatServices,metricRegistry)  
        1. dispatcherLeaderRetrievalService = highAvailabilityServices.getDispatcherLeaderRetriever()
      2. resourceManagerRetrievalService = highAvailabilityServices.getResourceManagerLeaderRetriever()
      3. LeaderGatewayRetriever<DispatcherGateway> dispatcherGatewayRetriever = new RpcGatewayRetriever
      4. LeaderGatewayRetriever<ResourceManagerGateway> resourceManagerGatewayRetriever = new RpcGatewayRetriever
      5. WebMonitorEndpoint webMonitorEndpoint = createRestEndpoint(
				configuration,
				dispatcherGatewayRetriever,
				resourceManagerGatewayRetriever,
				transientBlobCache,
				rpcService.getExecutor(),
				new AkkaQueryServiceRetriever(actorSystem, timeout),highAvailabilityServices.getWebMonitorLeaderElectionService()) `webMonitorEndpoint  : Rest endpoint which serves the web frontend REST calls`
        6. YarnResourceManager resourceManager = createResourceManager(
				configuration,
				ResourceID.generate(),
				rpcService,
				highAvailabilityServices,
				heartbeatServices,
				metricRegistry,
				this,
				clusterInformation,
				webMonitorEndpoint.getRestBaseUrl())  
 `ResourceManager implementation. The resource manager is responsible for resource de-/allocation and bookkeeping.  
It offers the following methods as part of its rpc interface to interact with him remotely: {@link #registerJobManager(JobMasterId, ResourceID, String, JobID, Time)} registers a {@link JobMaster} at the resource manager {@link #requestSlot(JobMasterId, SlotRequest, Time)} requests a slot from the resource manager`
       7. jobManagerMetricGroup = MetricUtils.instantiateJobManagerMetricGroup(metricRegistry, rpcService.getAddress())
       8. dispatcher = createDispatcher(
				configuration,
				rpcService,
				highAvailabilityServices,
resourceManager.getSelfGateway(ResourceManagerGateway.class),
				blobServer,
				heartbeatServices,
				jobManagerMetricGroup,
				metricRegistry.getMetricQueryServicePath(),
				archivedExecutionGraphStore,
				this,
				webMonitorEndpoint.getRestBaseUrl())

### JobMaster

### TaskExecutor
org.apache.flink.runtime.taskexecutor.TaskExecutor

### TaskManagerRunner
org.apache.flink.runtime.taskexecutor.TaskManagerRunner

### SlotManager
org.apache.flink.runtime.resourcemanager.slotmanager.SlotManager

### YarnResourceManager
/*  The yarn implementation of the resource manager. Used when the system is started via the resource framework YARN.*/
YarnResourceManager.onContainersAllocated  创建 taskExecutor

### Dispatcher


# 参考文献
1. Flink Deployment - YARN SETUP <https://ci.apache.org/projects/flink/flink-docs-release-1.4/ops/deployment/yarn_setup.html>
2. [FLIP-6 - Flink Deployment and Process Model - Standalone, Yarn, Mesos, Kubernetes, etc.](https://cwiki.apache.org/confluence/pages/viewpage.action?pageId=65147077)
3. kerberos认证原理---讲的非常细致，易懂<http://blog.csdn.net/wulantian/article/details/42418231>

