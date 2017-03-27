#!/bin/bash
if [ -z $5 ]; then
  echo "Use: $0 [test name] [min power] [max power] [step] [duration for each power]"
  echo "Example: $0 cool 3000 3250 50 10  (id 999, start in 30dBm, stop at 32.5dBm, hop .5 em .5 dBM, stay 10 seconds in each power)"
  echo "Example: $0 infinitely 3000 3000 1 0 (id 999, only 30dBm, runs infinitely)"
  exit
fi

test=$1;
minPower=$2;
maxPower=$3;
step=$4;
duration=$5;

clear=1;

for (( i = $minPower ; i <= $maxPower ; i = i+$step ))
do
  #java -cp .:/usr/share/java/postgresql.jar ReaderMain protocol=GEN2,antenna=1,repeat=0,power=$i $duration
  java -cp .:/usr/share/java/postgresql.jar ReaderMain debugflags=15,debugfile=debug.txt,protocol=GEN2,antenna=1,repeat=0,csv=1,jdbc=1,testname=$test,power=$i,cleanresults=$clear $duration
  clear=0;
#  sleep 1
done
