#!/bin/sh
set -eu

# 平台证书在容器运行时注入，不能假定它在镜像构建阶段就存在。
cloud_ca=${WECHAT_CA_CERT:-/app/cert/certificate.crt}
if [ -f "$cloud_ca" ]; then
  truststore=$(mktemp /tmp/qiyu-cacerts.XXXXXX)
  cp "$JAVA_HOME/lib/security/cacerts" "$truststore"
  echo '[TLS] Importing platform cloud-call certificate into dedicated Java truststore'
  keytool -importcert -noprompt -trustcacerts -alias qiyu-wechat-cloud-ca \
    -file "$cloud_ca" -keystore "$truststore" -storepass changeit
  echo '[TLS] Platform certificate imported; standard public roots retained'
  exec java "-Djavax.net.ssl.trustStore=$truststore" -Djavax.net.ssl.trustStorePassword=changeit \
    -Duser.timezone=Asia/Shanghai -Djava.awt.headless=true -jar /app/springboot-wxcloudrun-1.0.jar
fi

echo '[TLS] Platform cloud-call certificate not mounted; using standard Java truststore with DigiCert G2'
exec java -Duser.timezone=Asia/Shanghai -Djava.awt.headless=true -jar /app/springboot-wxcloudrun-1.0.jar
