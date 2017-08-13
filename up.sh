#!/bin/bash

envFile=".env"

if [ -f "$envFile" ]
then
  . $envFile

  sed -i "/server.domain=/ s/=.*/=$SERVER_DOMAIN/" ./server/src/main/resources/config.properties
  sed -i "/server.localDomain=/ s/=.*/=server/" ./server/src/main/resources/config.properties
  sed -i "/jdbc.database=/ s/=.*/=$POSTGRES_DB/" ./server/src/main/resources/config.properties
  sed -i "/jdbc.username=/ s/=.*/=$POSTGRES_USER/" ./server/src/main/resources/config.properties
  sed -i "/jdbc.password=/ s/=.*/=$POSTGRES_PASSWORD/" ./server/src/main/resources/config.properties
  sed -i "/recaptcha.secret=/ s/=.*/=$RECAPTCHA_SERVER/" ./server/src/main/resources/config.properties

  sed -i "/emailServer.path=/ s/=.*/=mail/" ./server/src/main/resources/config.properties
  sed -i "/emailServer.username=/ s/=.*/=$EMAIL_SERVER_USER/" ./server/src/main/resources/config.properties
  sed -i "/emailServer.password=/ s/=.*/=$EMAIL_SERVER_PASSWORD/" ./server/src/main/resources/config.properties

  sed -i "/var reCaptcha=/ s/=.*/=\"$RECAPTCHA_CLIENT\";/" ./static-page/src/js/actionLogin.js

  pushd static-page
  gradle build
  popd

  pushd server
  gradle shadowJar
  popd

  # prepare artifacts to deploy
  images=(static-apache static-nginx server postgres)
  for i in "${images[@]}"; do
    rm -R ./devops/$i/dist
    mkdir ./devops/$i/dist
  done

  # apache
  cp -R ./static-page/papka/* ./devops/static-apache/dist

  # nginx
  cp -R ./static-page/papka ./devops/static-nginx/dist
  cp -R ./devops/cert/share ./devops/static-nginx/dist

  cp ./devops/static-nginx/default.conf.template ./devops/static-nginx/default.conf
  sed -i "s/SERVER_DOMAIN_MOBILE/$SERVER_DOMAIN_MOBILE/" ./devops/static-nginx/default.conf
  sed -i "s/SERVER_DOMAIN_ALIAS/$SERVER_DOMAIN_ALIAS/" ./devops/static-nginx/default.conf
  sed -i "s/SERVER_DOMAIN/$SERVER_DOMAIN/" ./devops/static-nginx/default.conf
  sed -i "s/SERVER_LOCAL_DOMAIN/server/" ./devops/static-nginx/default.conf


  # java backend
  cp ./server/build/libs/papka-24.jar ./devops/server/dist
  cp -R ./server/src/main/resources/template/email ./devops/server/dist
  cp -R ./devops/logic ./devops/server/dist

  # postgres
  cp ./server/build/resources/main/sql/CreateDB.sql ./devops/postgres/dist

  sudo docker-compose up $1

else
  echo "'$envFile' not found."
  echo "copy '.env.template' to '$envFile' and update it according to your environment"
fi

