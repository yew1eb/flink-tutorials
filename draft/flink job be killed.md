**Recovery behavior of Flink on YARN**
Flink’s YARN client has the following configuration parameters to control how to behave in case of container failures. These parameters can be set either from the conf/flink-conf.yaml or when starting the YARN session, using -D parameters.

yarn.reallocate-failed: This parameter controls whether Flink should reallocate failed TaskManager containers. Default: true
yarn.maximum-failed-containers: The maximum number of failed containers the ApplicationMaster accepts until it fails the YARN session. Default: The number of initially requested TaskManagers (-n).
yarn.application-attempts: The number of ApplicationMaster (+ its TaskManager containers) attempts. If this value is set to 1 (default), the entire YARN session will fail when the Application master fails. Higher values specify the number of restarts of the ApplicationMaster by YARN.

**Log Files**
In cases where the Flink YARN session fails during the deployment itself, users have to rely on the logging capabilities of Hadoop YARN. The most useful feature for that is the YARN log aggregation. To enable it, users have to set the yarn.log-aggregation-enable property to true in the yarn-site.xml file. Once that is enabled, users can use the following command to retrieve all log files of a (failed) YARN session.

yarn logs -applicationId <application ID>
Note that it takes a few seconds after the session has finished until the logs show up.

**YARN Client console & Web interfaces**
The Flink YARN client also prints error messages in the terminal if errors occur during runtime (for example if a TaskManager stops working after some time).

In addition to that, there is the YARN Resource Manager web interface (by default on port 8088). The port of the Resource Manager web interface is determined by the yarn.resourcemanager.webapp.address configuration value.



It allows to access log files for running YARN applications and shows diagnostics for failed apps.

This is caused due to JVM settings in YARN container. You can refer to maillist thread http://apache-flink-user-mailing-list-archive.2336050.n4.nabble.com/Container-running-beyond-physical-memory-limits-when-processing-DataStream-td8188.html to understand more details. My settings are presented as above.