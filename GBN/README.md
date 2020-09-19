# Assignment 2: CS3205 - Computer Networks
### Name: Vimarsh Sathia
### Roll No: CS17B046

This file consists of instructions to run the program to simulate the Go-Back-N Protocol (on UDP Packets) for both the sender and receiver side. On a terminal, run the following commands:

1. **SenderGBN**:    
Do:  
`$>` `make sender`  
`$>` `java SenderGBN -p 12345 -l 512 -r 50 -n 400 -w 3 -b 10 `

2. **ReceiverGBN**:  
Do:  
`$>` `make receiver`  
`$>` `java ReceiverGBN -d -p 12345 -n 400 -e 0.0001 `

Make sure that the `-n` and `-p` flags are the same in the Sender and the Receiver. The complete description of flags is given below: 

* `-d` : toggle debug mode 
* `-s` \<string> : Receiver Name/IP address  
* `-p` \<integer> : Receiver Port
* `-l` \<integer> : Packet length  
* `-r` \<integer> : Packet generation rate
* `-n` \<integer> : Max packets
* `-w` \<integer> : Window size
* `-b` \<integer> : Max Buffer Size
* `-e` \<double> : Max drop probability  
