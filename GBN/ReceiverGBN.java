import java.io.*;
import java.net.*;

public class ReceiverGBN{
    
    //Command line arguments
    private boolean debug;
    private int port;
    private int max_packets;
    private double drop_prob;
    
    //Socket initialization
    private DatagramSocket socket;
    private boolean running;
    
    //Buffer initialization
    private byte buf[] = new byte[65535];
    private byte ack_buf[];
    public static void main(String args[])throws IOException{
        //Parse command line args.
        int debug = 0; //NODEBUG
        int port = 12345; //Default port
        int max_packets = 100; //Default number of packets
        double drop_prob = 0.0001; //Default drop probability
        
        for(int i = 0;i<args.length;++i){
            if(args[i].equals("-d"))debug = 1;
            else if(args[i].equals("-p")){port = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-n")){max_packets = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-e")){drop_prob = Double.parseDouble(args[++i]);}
            else ;
        }
        
        ReceiverGBN server = new ReceiverGBN(debug, port, max_packets, drop_prob);
        server.receivePackets();
        
    }
    
    public ReceiverGBN(int debug, int port, int max_packets, double drop_prob){
        
        this.port = port;
        this.debug = (debug==1);
        this.max_packets= max_packets;
        this.drop_prob = drop_prob;
        this.running = false;
        try{
            this.socket = new DatagramSocket(this.port);
        } catch (SocketException e){
            System.err.println("Couldn't create datagram socket: " + e.getMessage());
        }
        
    }
    
    void receivePackets()throws IOException{
        
        //Sequence numbers for packets start from
        int lastAckdSeqNum = 0;
        int expectedSeqNum = 1;
        long startTime = System.nanoTime();
        long endTime = System.nanoTime();;
        
        //Begin execution of Receiver
        running = true;
        while(running){
            try{
                //Declare a datagram packet
                DatagramPacket packet = new DatagramPacket(buf,buf.length);
                if(lastAckdSeqNum == this.max_packets){break;}
                //Receive packet- Exhibits blocking semantics
                socket.receive(packet);
                InetAddress senderAddress = packet.getAddress();
                int senderPort = packet.getPort();
                
                endTime = System.nanoTime();
                
                //Get raw string from byte array
                String raw_msg = new String(packet.getData(),0,packet.getLength(),"UTF-8");
                int packetSeqNum = getSequenceNumber(raw_msg);
                
                //Generate a random probability for dropping the packet.
                double p = Math.random();
                
                if(p > this.drop_prob){
                    
                    if(debug){
                        long millisec = (endTime - startTime)/(1000000);
                        long microsec = (endTime - startTime)/(1000);
                        System.out.println("Seq "+packetSeqNum+": Time Received: "+ millisec + ":" + microsec + " Packet dropped:  false"  );   
                    }
                    
                    //Check and update expectedSeqNum
                    if(packetSeqNum == expectedSeqNum){
                        lastAckdSeqNum = expectedSeqNum;
                        expectedSeqNum++;
                    }
                    
                    //Send an ACK for lastAckdSeqNum if > 0
                    if(lastAckdSeqNum > 0){
                        String ack_msg = raw_msg;
                        ack_buf = ack_msg.getBytes("UTF-8");
                        DatagramPacket ackPacket = new DatagramPacket(ack_buf,ack_buf.length,senderAddress,senderPort);
                        socket.send(ackPacket);
                    }
                    //Dont send an ack if lastAckdSeqNum = 0->initial packet is lost
                    //Check for termination
                    
                    if(lastAckdSeqNum == max_packets){
                        // System.out.println(lastAckdSeqNum);
                        running = false;
                        System.exit(0);
                        continue;
                    }
                }
                else{
                    //Drop packet.
                    // System.out.println("Packet dropped");
                    if(debug){
                        long millisec = (endTime - startTime)/(1000000);
                        long microsec = (endTime - startTime)/(1000);
                        System.out.println("Seq "+packetSeqNum+": Time Received: "+ millisec + ":" + microsec + " Packet dropped:  true" );   
                    }
                    
                }
            } catch (SocketTimeoutException e) {
                continue;
            } catch( SocketException e){
            }
            
        }
        
        // All packets ACKd
        this.socket.close();
    }
    
    int getSequenceNumber(String s){
        int seqNum = Integer.parseInt(s.substring(0, s.indexOf("$")));
        return seqNum;
    }
    
    String generateACKMessage(int ackNum){
        String str = "ack";
        str = "" + ackNum + str;
        return str;
    }
    
}