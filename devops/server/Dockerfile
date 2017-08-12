FROM openjdk:8-jre
MAINTAINER vladislav.kurmaz@gmail.com

COPY ./dist/papka-24.jar /home/papka-24.jar
COPY ./dist/email/ /var/www/mail_template/
RUN mkdir /home/logic
COPY ./dist/logic /home/logic

COPY ./process.sh /home/process.sh
CMD ["/home/process.sh"]