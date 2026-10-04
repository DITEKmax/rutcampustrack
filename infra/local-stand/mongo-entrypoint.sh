#!/bin/sh
set -eu
cp /run/rct-mongo/rs0.key /tmp/rct-local-rs0.key
chown mongodb:mongodb /tmp/rct-local-rs0.key
chmod 0400 /tmp/rct-local-rs0.key
exec docker-entrypoint.sh mongod --replSet rs0 --bind_ip_all --keyFile /tmp/rct-local-rs0.key
