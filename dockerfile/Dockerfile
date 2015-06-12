FROM tomcat:8

MAINTAINER Martin Rumanek <martin@rumanek.cz>

ENV REFRESHED_AT 2014-06-12

RUN curl -sL  http://ftp-devel.mzk.cz/AbbyyRest/AbbyyRest-1.0.war -o $CATALINA_HOME/webapps/AbbyyRest.war

ENV ABBYY_IN /data/IN
ENV ABBYY_OUT=/data/OUT
ENV ABBYY_EXCEPTION=/data/EXCEPTION

CMD ["catalina.sh", "run"]

