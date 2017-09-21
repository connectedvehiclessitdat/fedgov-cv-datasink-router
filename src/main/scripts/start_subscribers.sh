#!/bin/bash
#
# Runs the channel subscribers client program to listen for data on the
# specified topics. This program is designed to run on the Connected
# Vehicle Router instance for testing purposes.
#

source /etc/rtwsrc

RTWS_INGEST_OPTIONS="-DRTWS_ROOT_LOG_LEVEL=$RTWS_ROOT_LOG_LEVEL -DRTWS_SAIC_LOG_LEVEL=$RTWS_SAIC_LOG_LEVEL"

CLASSPATH_MOD="$(find /usr/local/rtws/ingest/lib -name '*.jar' | sort -r | tr -s '\n' ':'):."
java $RTWS_INGEST_OPTIONS -cp $CLASSPATH_MOD com.deleidos.cv.client.ChannelSubscriber
