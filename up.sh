#!/bin/bash

envFile=".env"

if [ -f "$envFile" ]
then
  . $envFile

  sed -i "/server.domain=/ s/=.*/=$SERVER_DOMAIN/" ./server/src/main/resources/config.properties
  sed -i "/jdbc.database=/ s/=.*/=$POSTGRES_DB/" ./server/src/main/resources/config.properties
  sed -i "/jdbc.username=/ s/=.*/=$POSTGRES_USER/" ./server/src/main/resources/config.properties
  sed -i "/jdbc.password=/ s/=.*/=$POSTGRES_PASSWORD/" ./server/src/main/resources/config.properties


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

  sudo docker-compose up $1

else
  echo "'$envFile' not found."
  echo "copy '.env.template' to '$envFile' and update it according to your environment"
fi

