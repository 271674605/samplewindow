#!/system/bin/sh
# Script to start "input" on the device, which has a very rudimentary
# shell.
#
base=/system
export CLASSPATH=$base/framework/samplewindow.jar
exec app_process $base/bin com.android.commands.samplewindow.SampleWindow "$@" 

