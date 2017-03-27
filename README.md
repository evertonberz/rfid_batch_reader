# RFID batch reader
This is a program to query RFID tags using a ThingMagic reader. 

It runs in batch mode and makes easy to collect the tag data. 
You can set the reader power range, a step value to hop the power, and a time limit for each power.

The program has two storage options: PostgreSQL database or CSV file. 
Run "runDB.sh" to store data collected in a PostgreSQL database or "runCSV.sh" to CSV files.

Use: runDB.sh [test name] [min power] [max power] [step] [duration for each power]
Examples:
- runDB.sh cool 3000 3250 50 10  # (id 999, start in 30dBm, stop at 32.5dBm, hop .5 em .5 dBM, stay 10 seconds in each power)
- runDB.sh infinitely 3000 3000 1 0 # (id 999, only 30dBm, runs infinitely)
