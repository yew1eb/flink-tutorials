

机器：gh-data-rt-rttest-test02

gh-data-rt-rttest-test16

日志目录：/var/sankuai/logs/hadoop/sankuai

resourcemanager hostname: http://gh-data-rt-rttest-test16.corp.sankuai.com:8088/cluster/apps/RUNNING



source init_env.sh

```
${FLINK_DIR}/bin/flink run -m yarn-cluster -yq root.hadoop-rt.flinkpub -yn 2 -ys 4 -yD yarn.container-start-command-template="/usr/local/jdk1.8.0_112/bin/java %jvmmem% %jvmopts% %logging% %class% %args% %redirects%" -p 6    {FLINK_DIR}/examples/batch/WordCount.jar

```



bin/flink modify 87973193471f038cfc88befb103c1cd0 -p 3

 /opt/meituan/hadoop-client/bin/yarn application -kill application_1526881879572_148514



/opt/meituan/hadoop-client/bin/hadoop -version

