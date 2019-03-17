athenax代码赏析：
com.uber.athenax.backend.server.jobs.JobManager.compile方法

## athenax-vm-compiler
三个component：
+ executor：真正完成所谓的”编译“工作，这里编译之所以加引号，其实只是借助于Flink的API得到对应的JobGraph；
+ planer：计划器，该模块的入口，它会顺序调用parser、validator、executor，最终得到一个称之为作业编译结果的JobCompilationResult对象；
+ parser：编译器，这里主要是针对其对SQL的扩展提供相应的解析实现，主要是对Calcite api的实现，最终得到SqlNode集合SqlNodeList；


这里，值得一提的是其”编译“的实现机制。AthenaX最终是要将其SQL Job提交给Flink运行时去执行，而对Flink而言JobGraph是其唯一识别的Job描述的对象，
所以它最关键的一点就是需要得到其job的JobGraph。那么它是如何做到这一点的？

### JobGraph的生成
它（JobCompiler）通过mock出一个利用Flink的Table&SQL API编写的Table&SQL 程序模板 :
com.uber.athenax.vm.compiler.executor.main()
```
StreamExecutionEnvironment execEnv = StreamExecutionEnvironment.createLocalEnvironment();
StreamTableEnvironment env = StreamTableEnvironment.getTableEnvironment(execEnv);
execEnv.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
CompilationResult res = new CompilationResult();

try {
    JobDescriptor job = getJobConf(System.in);
    res.jobGraph(new JobCompiler(env, job).getJobGraph());
} catch (Throwable e) {
    res.remoteThrowable(e);
}
```
JobDescriptor类包含了SQL作业的所有信息：
```
public class JobDescriptor implements Serializable {
  private static final long serialVersionUID = -1;
  private final Map<String, String> userDefineFunctions;
  private final Map<String, AthenaXTableCatalog> inputs;
  private final AthenaXTableCatalog outputs;
  private final int parallelism;

  /**
   * Stripped down statement that can be recognized by Flink.
   */
  private final String sqlStatement;
```

核心在于上面的getJobGraph方法`res.jobGraph(new JobCompiler(env, job).getJobGraph());`
```
  JobGraph getJobGraph() throws IOException {
    StreamExecutionEnvironment exeEnv = env.execEnv();
    exeEnv.setParallelism(job.parallelism());
    this
        .registerUdfs()
        .registerInputCatalogs();
    Table table = env.sql(job.sql());
    for (String t : job.outputs().listTables()) {
      table.writeToSink(getOutputTable(job.outputs().getTable(t)));
    }
    StreamGraph streamGraph = exeEnv.getStreamGraph();
    return streamGraph.getJobGraph();
  }
```

其中调用env.sql()这个方法说明它本质没能真正脱离Flink Table&SQL

设置完成之后，通过调用StreamExecutionEnvironment#getStreamGraph就可以自动获得JobGraph对象，
因此JobGraph的生成还是由Flink 自己提供的，而AthenaX只需要拼凑并触发该对象的生成。

生成后会通过flink的yarn client实现，将JobGraph提交给YARN集群，并启动Flink运行时执行Job。

而具体的触发机制，这里AthenX采用了运行时执行构造命令行执行JobCompiler的方法，
然后利用套接字+标准输出重定向的方式，来模拟UNIX PIPELINE，事实上个人认为没必要这么绕弯路，直接调用就行了。

## 解析器的代码生成
值得一提的是，parser涉及到具体的语法，这一块为了体现灵活性。
AthenaX将解析器的实现类跟SQL语法绑定在一起通过fmpp(文本模板预处理器)的形式进行代码生成。

fmpp是一个支持freemark语法的文本预处理器。


com.uber.athenax.backend.server.jobs.compile
```
  public JobCompilationResult compile(JobDefinition job, JobDefinitionDesiredstate spec) throws Throwable {
    Map<String, AthenaXTableCatalog> inputs = catalogProvider.getInputCatalog(spec.getClusterId());
    AthenaXTableCatalog output = catalogProvider.getOutputCatalog(spec.getClusterId(), job.getOutputs());
    Planner planner = new Planner(inputs, output);
    return planner.sql(job.getQuery(), Math.toIntExact(spec.getResource().getVCores()));
  }
```
