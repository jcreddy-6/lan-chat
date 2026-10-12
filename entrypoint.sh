#!/bin/sh
set -e

export PORT="${PORT:-10000}"

rm -f /etc/nginx/sites-enabled/default

envsubst '${PORT}' < /app/nginx.conf.template > /etc/nginx/conf.d/default.conf

echo "Starting LANChat Java server on internal port 5000..."
PORT=5000 java --enable-native-access=ALL-UNNAMED -cp "out:lib/*" com.lanchat.server.ChatServer &
JAVA_PID=$!

echo "Starting Nginx web server on external port $PORT..."
nginx -g "daemon off;" &
NGINX_PID=$!

trap "kill -TERM $JAVA_PID $NGINX_PID" INT TERM
wait
