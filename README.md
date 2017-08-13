# Papka24
## Quick start with docker compose
### Prepare environment
* Grab Ubuntu VM (16.x)
* Clone repository
* Run **./devops/prereq-ubuntu.sh**
### Prerequisites
* Copy **.env.template** to **.env** and update it according to your environment
```
SERVER_DOMAIN=<ip adress>|<FQDN>
SERVER_DOMAIN_ALIAS=www.<FQDN>
SERVER_DOMAIN_MOBILE=m.<FQDN>

SERVER_TYPE=nginx | apache

POSTGRES_USER=papka24
POSTGRES_PASSWORD=papka24
POSTGRES_DB=papka24

RECAPTCHA_CLIENT=
RECAPTCHA_SERVER=

EMAIL_SERVER_DOMAIN=mail.example.com
EMAIL_SERVER_USER=user
EMAIL_SERVER_PASSWORD=password
```
### Register reCAPTCHA
* Register your domain using https://www.google.com/recaptcha/admin
* Fill two properties **RECAPTCHA_CLIENT**, **RECAPTCHA_SERVER** in **.env** file with received keys 
### Provide server side certificates for your domain/ip-address
* dive into **<project_home>/devops/cert** folder
* execute **./gen-cert.sh**
* or copy already exising certificates into **<project_home>/devops/cert/share** folder
### Run cluster
* Run cluster **./up.sh** (**./up.sh -d** detached mode)
