#!/bin/bash

[ -z "$SONAR_HOME" ] && { echo "Please configure SONAR_HOME"; exit 1; }

mvn clean install || exit 1

PLUGIN_VERSION=1.0.0
PLUGIN_FILE="./target/sonar-bitbucket-plugin-$PLUGIN_VERSION.jar"

if [ ! -f $PLUGIN_FILE ]; then
    echo "Plug-in JAR file not found: $PLUGIN_FILE"
    exit 1
fi

$SONAR_HOME/bin/macosx-universal-64/sonar.sh stop

rm $SONAR_HOME/extensions/plugins/sonar-bitbucket-plugin-*
cp $PLUGIN_FILE $SONAR_HOME/extensions/plugins/sonar-bitbucket-plugin-$PLUGIN_VERSION.jar

$SONAR_HOME/bin/macosx-universal-64/sonar.sh start
