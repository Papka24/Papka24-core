#!/bin/bash

docker run -p 25:25 -e maildomain=mail.example.com \
  -e smtp_user=user:password --name postfix -d catatnight/postfix