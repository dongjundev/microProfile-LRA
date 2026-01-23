# microProfile-LRA
## narayana coordinator
docker run --rm --name lra-coordinator --add-host=host.docker.internal:host-gateway -p 8080:8080 \
      -e QUARKUS_HTTP_ACCESS_LOG_ENABLED=true \
      -e QUARKUS_LOG_LEVEL=INFO \
      quay.io/jbosstm/lra-coordinator
## order h2
http://localhost:8083/h2-console
## inventory h2
http://localhost:8081/h2-console
## payment h2
http://localhost:8082/h2-console
