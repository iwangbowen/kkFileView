FROM keking/kkfileview-base:4.4.0
ADD server/target/kkFileView-*.tar.gz /opt/
ENV KKFILEVIEW_BIN_FOLDER=/opt/kkFileView-4.4.1/bin
ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-Dspring.config.location=/opt/kkFileView-4.4.1/config/application.properties","-jar","/opt/kkFileView-4.4.1/bin/kkFileView-4.4.1.jar"]
