#!/bin/bash

echo "This is the message body and contains the message" | \
  mailx -v -r "user@mail.example.com" \
  -s "This is the subject" \
  -S smtp="localhost:25" \
  -S smtp-use-starttls \
  -S smtp-auth=login \
  -S smtp-auth-user="user@mail.example.com" \
  -S smtp-auth-password="password" \
  -S ssl-verify=ignore \
  vladislav.kurmaz@gmail.com