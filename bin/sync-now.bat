@echo off

rem Run GCALDaemon once then quit

java -Xmx256m -cp ../lib/commons-codec.jar;../lib/commons-lang.jar;../lib/commons-logging.jar;../lib/gcal-daemon.jar;../lib/gdata-calendar.jar;../lib/gdata-client.jar;../lib/ical4j.jar;../lib/logger.jar;../lib/commons-collections.jar;../lib/commons-io.jar;../lib/shared-asn1.jar;../lib/shared-ldap.jar;../lib/rome.jar;../lib/commons-httpclient.jar;../lib/jdom.jar;../lib/mail.jar;../lib/activation.jar org.gcaldaemon.standalone.Main "C:/Progra~1/GCALDaemon/conf/gcal-daemon.cfg" runonce
