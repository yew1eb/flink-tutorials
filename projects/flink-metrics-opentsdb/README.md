

## 本地环境测试

### 1. 启动opentsdb
docker run -d --name=opentsdb -p 4242:4242 petergrace/opentsdb-docker

### 2. 启动granfana
docker run -d --name=grafana -p 3000:3000 grafana/grafana