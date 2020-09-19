Assignment 2 Report:CS3205 - Computer Networks 
===========================================
## Name: Vimarsh Sathia  
## Roll No: CS17B046


In this assignment, we had to implement the Go-Back-N and Selective Repeat protocol used in reliable data transfer. The results of both protocol implementations in Java are summarized below:  

### Go-Back-N Protocol

The results for a low and high packet generation rate are summarised in the following tables:  
In every case, 500 packets were sent, window size was 3, and max buffer size is 30.  
 
* Packet Generation Rate = 30 packets/sec  

| Trial | Packet length(bytes) | Drop probability | RTT(in ms) | Retransmission Ratio |
|:-----:|:--------------------:|:----------------:|:----------:|:--------------------:|
|   1   | 512                  |  10<sup>-3</sup> |    0.729   |       1.0175         |
|   2   | 512                  |  10<sup>-5</sup> |    0.657   |       1.0125         |
|   3   | 512                  |  10<sup>-7</sup> |    0.643   |       1.0125         |
|   1   | 1024                 |  10<sup>-3</sup> |    0.752   |       1.015          |
|   2   | 1024                 |  10<sup>-5</sup> |    0.730   |       1.0125         |

* Packet Generation Rate = 180 packets/sec

| Trial | Packet length(bytes) | Drop probability | RTT(in ms) | Retransmission Ratio |
|:-----:|:--------------------:|:----------------:|:----------:|:--------------------:|
|   1   | 512                  |  10<sup>-3</sup> |    0.690   |       1.0129         |
|   2   | 512                  |  10<sup>-5</sup> |    0.708   |       1.0000         |
|   3   | 512                  |  10<sup>-7</sup> |    0.681   |       1.0000         |
|   1   | 1024                 |  10<sup>-3</sup> |    0.601   |       1.0175         |
|   2   | 1024                 |  10<sup>-5</sup> |    0.654   |       1.0150         |


### Selective Repeat Protocol
The results for a low and high packet generation rate are summarised in the following tables:  
In every case, 500 packets were sent, with a receiver and sender window size of 3, and max buffer size of 30.

* Packet Generation Rate = 30 packets/sec  

| Trial | Packet length(bytes) | Drop probability | RTT(in ms) | Retransmission Ratio |
|:-----:|:--------------------:|:----------------:|:----------:|:--------------------:|
|   1   | 512                  |  10<sup>-3</sup> |    0.180   |       1.0000         |
|   2   | 512                  |  10<sup>-5</sup> |    0.185   |       1.0000         |
|   3   | 512                  |  10<sup>-7</sup> |    0.171   |       1.0000         |
|   1   | 1024                 |  10<sup>-3</sup> |    0.176   |       1.0125         |
|   2   | 1024                 |  10<sup>-5</sup> |    0.181   |       1.0000         |

* Packet Generation Rate = 180 packets/sec

| Trial | Packet length(bytes) | Drop probability | RTT(in ms) | Retransmission Ratio |
|:-----:|:--------------------:|:----------------:|:----------:|:--------------------:|
|   1   | 512                  |  10<sup>-3</sup> |    0.152   |       1.0175         |
|   2   | 512                  |  10<sup>-5</sup> |    0.159   |       1.0125         |
|   3   | 512                  |  10<sup>-7</sup> |    0.150   |       1.0000         |
|   1   | 1024                 |  10<sup>-3</sup> |    0.141   |       1.0000         |
|   2   | 1024                 |  10<sup>-5</sup> |    0.142   |       1.0000         |

### Inferences

When the firing rate increases, the RTT is decreasing. This is probably due to the way RTT is measured. Also, since we dont have to flush and repeat the entire pipeline in the Selective Repeat Protocol, Selective Repeat has a much better RTT and retransmission ratio compared to Go Back N.


