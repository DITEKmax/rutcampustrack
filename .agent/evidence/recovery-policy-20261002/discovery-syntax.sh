docker inspect --format '{{.Name}} {{.Config.Image}} {{.Config.Hostname}} {{index .Config.Labels "com.docker.compose.project"}}' rct-redis rct-rabbitmq
docker inspect --format '{{json .Mounts}}' rct-redis rct-rabbitmq
# REDISCLI_AUTH заранее предоставлен оператору защищённым способом, не аргументом -a.
docker exec -e REDISCLI_AUTH rct-redis redis-cli --no-auth-warning INFO persistence
docker exec -e REDISCLI_AUTH rct-redis redis-cli --no-auth-warning CONFIG GET dir
docker exec -e REDISCLI_AUTH rct-redis redis-cli --no-auth-warning CONFIG GET dbfilename
docker exec -e REDISCLI_AUTH rct-redis redis-cli --no-auth-warning CONFIG GET save
docker exec -e REDISCLI_AUTH rct-redis redis-cli --no-auth-warning CONFIG GET appendonly
docker exec -e REDISCLI_AUTH rct-redis redis-cli --no-auth-warning INFO memory
docker exec -e REDISCLI_AUTH rct-redis redis-cli --no-auth-warning DBSIZE
docker exec rct-rabbitmq rabbitmq-diagnostics -q status
docker exec rct-rabbitmq rabbitmqctl list_vhosts name
docker exec rct-rabbitmq rabbitmqctl list_queues -p / name durable messages_ready messages_unacknowledged consumers
docker exec rct-rabbitmq rabbitmqctl list_bindings -p / source_name destination_name destination_kind routing_key
