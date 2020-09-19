import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ReceiverSR{
    
    //Command line arguments
    private boolean debug;
    private int port;
    private int max_packets;
    private double drop_prob;
    private int sequenceFieldLength;
    private int windowSize;
    private int bufferSize;
    private ConcurrentHashMap<Integer,DatagramPacket> rBuffer;
    private ConcurrentHashMap<Integer,Long> timeStamp;

    private int base;
   
    //Socket initialization
    private DatagramSocket socket;
    // private boolean running;
    
    //Buffer initialization
    private byte buf[] = new byte[65535];
    // private byte ack_buf[];

    public static void main(String args[])throws IOException{
        //Parse command line args.
        int debug = 0; //NODEBUG
        int port = 12345; //Default port
        int max_packets = 100; //Default number of packets
        double drop_prob = 0.0001; //Default drop probability
        int sequenceFieldLength = 1;
        int windowSize = 3;
        int bufferSize = 10;

        for(int i = 0;i<args.length;++i){
            if(args[i].equals("-d"))debug = 1;
            else if(args[i].equals("-p")){port = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-N")){max_packets = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-e")){drop_prob = Double.parseDouble(args[++i]);}
            else if(args[i].equals("-n")){sequenceFieldLength = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-W")){windowSize = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-B")){bufferSize = Integer.parseInt(args[++i]);}
            else ;
        }
        
        ReceiverSR server = new ReceiverSR(debug, port, max_packets, drop_prob, sequenceFieldLength, windowSize, bufferSize);
        server.receivePackets();
    }
    
    public ReceiverSR(int debug, int port, int max_packets, double drop_prob,int sequenceFieldLength,int windowSize,int bufferSize){
        
        this.port = port;
        this.debug = (debug==1);
        this.max_packets= max_packets;
        this.drop_prob = drop_prob;
        this.windowSize = windowSize;
        this.sequenceFieldLength = sequenceFieldLength;
        this.bufferSize = bufferSize;
        this.base = 1;

        this.rBuffer = new ConcurrentHashMap<Integer,DatagramPacket>();
        this.timeStamp = new ConcurrentHashMap<Integer,Long>();
        // this.running = false;
        
        //Open socket
        try{
            this.socket = new DatagramSocket(this.port);
        } catch (SocketException e){
            System.err.println("Couldn't create datagram socket: " + e.getMessage());
        }
    }

    void logPacket(long startTime, long endTime, int seqnum){
        timeStamp.putIfAbsent(seqnum, endTime - startTime);
    }

    void receivePackets()throws IOException{

        long startTime = System.nanoTime();
        long endTime=0;

        while(base <= this.max_packets){
            DatagramPacket packet = new DatagramPacket(buf,buf.length);
            this.socket.receive(packet);
            endTime = System.nanoTime();
            
            double p = Math.random();
            
            //discard packet
            if(p < drop_prob){ 
                if(debug){
                    // System.out.println("Packet dropped: ");
                }
                continue; 
            }
            
            //Get raw string from byte array
            String msg = new String(packet.getData(),0,packet.getLength(),"UTF-8");
            int seqnum = getSequenceNumber(msg);

            //All packets before base have been correctly ackd.
            if(seqnum < base && seqnum >= base - this.windowSize){
                //Send packet back to sender.
                packet = new DatagramPacket(packet.getData(), packet.getLength(), packet.getAddress(), packet.getPort());
                this.socket.send(packet);
                //Dont put packet in buffer for waiting requests.
            }
            else if(seqnum >= base && seqnum < base + windowSize){
                
                //Log sequence information
                logPacket(startTime,endTime,seqnum);

                //Receiving packet for maybe first time.
                rBuffer.put(seqnum, packet);
                //send an ACK
                packet = new DatagramPacket(packet.getData(), packet.getLength(), packet.getAddress(), packet.getPort());
                this.socket.send(packet);
                
                if(seqnum == base){
                    //remove elements from receive buffer and increment base
                    while(rBuffer.containsKey(base)){
                        if(debug){
                            //Print sequence information.
                            long time = timeStamp.get(base);
                            // System.out.println(time);
                            System.out.printf("Seq %d Time Received: %d:%d \n",base,time/1000000,(time%1000000 - time%1000)/1000);
                        }
                        rBuffer.remove(base);
                        base++;
                    }
                }
            }
            else{
                //Ignore packet
                
            }
        }   
    }
    
    int getSequenceNumber(String msg){
        int seqNum = Integer.parseInt(msg.substring(0, msg.indexOf("$")));
        return seqNum;
    }
    
}