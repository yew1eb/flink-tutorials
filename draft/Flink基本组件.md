## JobManager
Library cache setup

execution graph creation, attaching to previous graph

master side initialization

streaming fault tolerance coordinator start

trigger scheduling

deploy task executions

参考文献:
<http://chenyuzhao.me/2017/02/08/jobmanager%E5%9F%BA%E6%9C%AC%E7%BB%84%E4%BB%B6/>
 

## TaskManager
Receive Task deployment

download libraries (if needed)

setup input / output readers and writers

create task thread

send status updates

参考文件:
<http://chenyuzhao.me/2017/02/09/taskmanager%E5%9F%BA%E6%9C%AC%E7%BB%84%E4%BB%B6/>
