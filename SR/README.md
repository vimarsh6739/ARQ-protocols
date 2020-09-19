# Assignment 2: CS3205 - Computer Networks
### Name: Vimarsh Sathia
### Roll No: CS17B046

This file consists of instructions to run the program to simulate the Selective Repeat Protocol (on UDP Packets) for both the sender and receiver side. On a terminal, run the following commands:

1. **SenderSR**:    
Do:  
`$>` `make sender`  
`$>` `java SenderSR -p 12345 -L 512 -R 100 -N 400 -B 100 -n 8`

2. **ReceiverSR**:  
Do:  
`$>` `make receiver`  
`$>` `java ReceiverSR -d -p 12345 -N 400 -e 0.01 -B 100  `  
The debug output time represents the time at which the receiver got the packet since it started.

Make sure that the `-n` and `-p` flags are the same in the Sender and the Receiver. The complete description of flags is given below: 

* `-d` : toggle debug mode 
* `-s` \<string> : Receiver Name/IP address  
* `-p` \<integer> : Receiver Port
* `-n` \<integer> : Sequence field length (in bits)
* `-L` \<integer> : Max Packet length  
* `-R` \<integer> : Packet generation rate
* `-N` \<integer> : Max packets
* `-W` \<integer> : Window size
* `-B` \<integer> : Max Buffer Size
* `-e` \<double> : Max drop probability