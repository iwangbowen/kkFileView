FROM kkfileview-base:latest
ADD server/target/kkFileView-*.tar.gz /opt/
ENV KKFILEVIEW_BIN_FOLDER=/opt/kkFileView-5.0.0/bin
ENTRYPOINT ["sh","-c","LO_PY_CORE_DIR=$(ls -d /opt/libreoffice*/program/python-core-* 2>/dev/null | head -n1); if [ -n \"$LO_PY_CORE_DIR\" ]; then export PYTHONHOME=\"$LO_PY_CORE_DIR\"; export PYTHONPATH=\"$LO_PY_CORE_DIR/lib\"; else unset PYTHONHOME PYTHONPATH; fi; exec java -Dfile.encoding=UTF-8 -Dspring.config.location=/opt/kkFileView-5.0.0/config/application.properties -jar /opt/kkFileView-5.0.0/bin/kkFileView-5.0.0.jar"]
