#!/bin/bash
sudo apt-get update
sudo apt-get -y upgrade
sudo apt-get install -y mc

sudo apt-get install -y default-jre
sudo apt-get install -y default-jdk

sudo apt-get install -y \
    apt-transport-https \
    ca-certificates \
    gradle \
    curl \
    software-properties-common
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
sudo apt-key fingerprint 0EBFCD88
sudo add-apt-repository \
   "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
   $(lsb_release -cs) \
   stable"
sudo apt-get update
sudo apt-get install -y docker-ce

sudo curl -L "https://github.com/docker/compose/releases/download/1.11.2/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

sudo add-apt-repository ppa:cwchien/gradle
sudo apt-get update
sudo apt-get install gradle-4.0

sudo apt-get install -y nodejs npm
