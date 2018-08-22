#!/usr/bin/env bash
SHELL_SCRIPT_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_PATH=${SHELL_SCRIPT_PATH}/../lib
export CLASSPATH=${CLASSPATH}:${LIB_PATH}/*
source $SHELL_SCRIPT_PATH/env.sh
echo $SHELL_SCRIPT_PATH
echo $LIB_PATH
echo $CLASSPATH
echo $JAVA_OPTIONS
java "$JAVA_OPTIONS" -classpath ${CLASSPATH}:${LIB_PATH}/bee-cache-core-1.0-SNAPSHOT.jar com.gabry.beecache.core.server.BeeCacheStoreServer

