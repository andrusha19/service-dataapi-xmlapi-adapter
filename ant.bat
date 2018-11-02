@echo off
set ANT_OPTS=-Dfile.encoding=UTF-8
../../bin/ant/bin/ant -f nonb-build.xml %*
