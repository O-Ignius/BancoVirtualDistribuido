#!/bin/bash

RAIZ=$(pwd)
LIBS=$(cd ../../libs/ && pwd)
LIBS="$LIBS/jgroups-5.0.0.Final.jar"

export CLASSPATH="${CLASSPATH:+$CLASSPATH:}$RAIZ:$LIBS"
