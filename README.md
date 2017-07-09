# Papka24
## Prerequisites
### Register reCAPTCHA
* Register your domain https://www.google.com/recaptcha/admin
* Fill two properties **RECAPTCHA_CLIENT**, **RECAPTCHA_SERVER** in .env file with received keys 

## Quick start with docker compose
* Grab Ubuntu VM (16.x)
* Clone repository
* Run './devops/prereq-ubuntu.sh'
* Copy '.env.template' to '.env' and update it according to your environment
* Run cluster './up.sh' ('./up.sh -d' detached mode)
