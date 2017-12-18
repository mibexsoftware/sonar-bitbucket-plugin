#!/bin/bash

[ -z "$SONAR_HOME" ] && { echo "Please configure SONAR_HOME"; exit 1; }

mvn clean install || exit 1

PLUGIN_VERSION=1.2.3
PLUGIN_FILE="./target/sonar-bitbucket-plugin-$PLUGIN_VERSION.jar"

[ ! -f $PLUGIN_FILE ] && { echo "Plug-in JAR file not found: $PLUGIN_FILE"; exit 1; }

$SONAR_HOME/bin/macosx-universal-64/sonar.sh stop

rm $SONAR_HOME/extensions/plugins/sonar-bitbucket-plugin-*
cp $PLUGIN_FILE $SONAR_HOME/extensions/plugins/sonar-bitbucket-plugin-$PLUGIN_VERSION.jar

$SONAR_HOME/bin/macosx-universal-64/sonar.sh start
