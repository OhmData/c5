#!/bin/sh
set -ex
MAVEN_JAR=target/c5db-0.1-SNAPSHOT-jar-with-dependencies.jar
if [ -f ${MAVEN_JAR} ]
then
	echo "skiping maven"
else
	mvn assembly:single
fi
java -DtestTable -cp ${MAVEN_JAR}:target/c5db-0.1-SNAPSHOT.jar c5db.Main 
