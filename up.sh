#!/bin/bash

sed -i "/server.domain=/ s/=.*/=1111/" ./server/src/main/resources/config.properties

: '
pushd static-page
gradle build
popd

pushd server
gradle shadowJar
popd

rm -R ./devops/static/dist
mkdir ./devops/static/dist
cp -R ./static-page/papka/* ./devops/static/dist

rm -R ./devops/server/dist
mkdir ./devops/server/dist
cp ./server/build/libs/papka-24.jar ./devops/server/dist

rm -R ./devops/postgres/dist
mkdir ./devops/postgres/dist
cp ./server/build/resources/main/sql/CreateDB.sql ./devops/postgres/dist

docker-compose up
'
