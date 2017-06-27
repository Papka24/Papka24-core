#!/bin/bash

: '
pushd static-page
gradle build
popd
'
pushd server
gradle shadowJar
popd
: '
rm -R ./devops/static/dist
mkdir ./devops/static/dist
cp -R ./static-page/papka/* ./devops/static/dist
'
rm -R ./devops/server/dist
mkdir ./devops/server/dist
cp ./server/build/libs/papka-24.jar ./devops/server/dist

docker-compose up
