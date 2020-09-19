import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class SenderSR{
    
    //Command line arguments    
    private volatile boolean debug ;     //DEBUG flag
    private String address;     //IP Address
    private volatile int port;           //Receiver port
    private volatile int packetLength;   //Length of UDP Packet to be sent
    private volatile int packetGenRate;  //Generation rate of packets
    private volatile int maxPackets;     //Max number of packets to be sent
    private volatile int windowSize;     //Size of window/Thread pool
    private volatile int maxBufferSize;  //Size of Buffer
    private volatile int seqnumFieldLength; //Size of sequence field number

    //Socket initialization
    InetAddress ipAddress; //Receiver IP Address
    DatagramSocket socket; //Socket to send data to receiver
    
    //Variables for only acknowledgement listener thread
    byte[] ack_buf = new byte[65535];  //Receive ack output on ack buf
    byte[] send_buf;
    
    volatile boolean running;   //Variable to terminate program

    // Variables for sending packets
    volatile int base;
    volatile int nextseqnum;

    ScheduledThreadPoolExecutor timeoutScheduler; 
    ConcurrentHashMap<Integer,ScheduledFuture<?>> packetTimeoutMap;
    ConcurrentHashMap<Integer,Long> packetStartTime;
    ConcurrentHashMap<Integer,Integer> packetTrialNum;
    ConcurrentHashMap<Integer,Long> RTTPacket;

    ScheduledThreadPoolExecutor messageExecutor;
    ConcurrentHashMap<Integer,String> messageBuffer;
    ConcurrentHashMap<Integer,DatagramPacket> rBuffer;
    
    volatile int bufferSize;
    volatile int numPacketsGenerated;
    volatile int messageSeqNum;

    volatile double RTT_avg;
    volatile int ackCount;
    volatile long timeoutTime;

    //Lock to maintain mutual exclusion to variables
    private final Object lock = new Object();
    private final Object lock2 = new Object();
    volatile int sendCount;
    public static void main(String[] args) {
        
        //Default arguments in given example
        boolean debug = false;
        String address = "localhost";
        int port = 12345;
        int packetLength = 512;
        int packetGenRate = 10;
        int maxPackets = 400;
        int windowSize = 3;
        int maxBufferSize = 10;
        int seqnumFieldLength = 1;
        
        //Process args from commad line
        for(int i=0;i<args.length;++i){
            if(args[i].equals("-d")){debug = true;}
            else if(args[i].equals("-s")){address = args[++i];}
            else if(args[i].equals("-p")){port = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-l")){packetLength = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-R")){packetGenRate = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-N")){maxPackets = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-W")){windowSize = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-B")){maxBufferSize = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-n")){seqnumFieldLength = Integer.parseInt(args[++i]);}
            else ;
        }
        
        SenderSR client = new SenderSR(debug, address, port, packetLength, packetGenRate, maxPackets, windowSize, maxBufferSize, seqnumFieldLength);
        client.setupThreads();
        client.rdt_send();
        client.cleanupThreads();
        // System.out.println(client.RTT_avg);
    }
    
    public SenderSR(boolean debug, String address, int port, int packetLength, int packetGenRate, int maxPackets, int windowSize, int maxBufferSize, int seqnumFieldLength){
        
        // Initialize command line arguments
        this.debug = debug;
        this.address = address;
        this.port = port;
        this.packetLength = packetLength;
        this.packetGenRate = packetGenRate;
        this.maxPackets = maxPackets;
        this.windowSize = windowSize;
        this.maxBufferSize = maxBufferSize;    
        this.seqnumFieldLength = seqnumFieldLength;
        this.sendCount = 0;
        //Get the ip address of the receiver and create a datagram socket
        try {
            this.ipAddress = InetAddress.getByName(this.address);
            this.socket = new DatagramSocket();
        } catch (UnknownHostException e) {
            //Couldnt find IP Address
            e.printStackTrace();
        } catch(SocketException e){
            //Couldnt open socket at specified port
            e.printStackTrace();
        }
        
        //Initialize other parameters
        this.base = 1;
        this.nextseqnum = 1;
        this.timeoutScheduler = new ScheduledThreadPoolExecutor(this.windowSize);
        this.timeoutScheduler.setRemoveOnCancelPolicy(true);
        this.packetTimeoutMap = new ConcurrentHashMap<Integer,ScheduledFuture<?>>();
        this.packetStartTime = new ConcurrentHashMap<Integer,Long>();
        this.packetTrialNum = new ConcurrentHashMap<Integer,Integer>();
        
        //Initialize trial number for packets
        for(int i = 1;i <= this.maxPackets; ++i){
            this.packetTrialNum.put(i,1);
        }

        this.RTTPacket = new ConcurrentHashMap<Integer,Long>();

        this.RTT_avg = 0.0d;
        this.ackCount = 0;
        this.timeoutTime = 300; //Initial value of timeout in ms
        
        //Generating packets
        this.messageExecutor = new ScheduledThreadPoolExecutor(1);
        this.messageBuffer = new ConcurrentHashMap<Integer,String>();
        this.messageSeqNum = 1;
        this.bufferSize = 0;
        this.numPacketsGenerated = 0;
        this.rBuffer = new ConcurrentHashMap<Integer,DatagramPacket>();
        //Start sending packets
        this.running = true;
    }

    public void setupThreads(){
        //Start ack thread
        ACKThread ackT = new ACKThread();
        ackT.start();

        //Start generating packets
        PacketGenerator gen = new PacketGenerator();
        gen.start();
        //Generate a packet every 1000/r millisec
        //this.messageExecutor.scheduleAtFixedRate(gen, 0,(long)(1000/this.packetGenRate) ,TimeUnit.MILLISECONDS);

    }

    public void cleanupThreads(){
        this.messageExecutor.shutdown();
        this.timeoutScheduler.shutdown();
        this.socket.close();
        System.out.println("PACKET_GEN_RATE: " + this.packetGenRate);
        System.out.println("PACKET_LENGTH: " + this.packetLength);
        System.out.println("Retransmission Ratio: " + 1.0*this.sendCount/this.ackCount);
        System.out.println("RTT(in nanoseconds): " +RTT_avg);
    }

    //Called by packet generator thread
    public String createMessage(int msgSize,int seqnum){

        // Java chars are 2 bytes
        msgSize = msgSize/2;

        StringBuilder sb = new StringBuilder(msgSize);
        for (int i=0; i<msgSize-this.seqnumFieldLength; i++) {
            sb.append('$');
        }
        String str = sb.toString();
        str = "" + seqnum + str;
        return str;
    }

    public synchronized void sendData(DatagramPacket packet,int seqnum){
        //Send packet to IP Address
        try { this.socket.send(packet);
        } catch (IOException e) {}

        //Start a timer for the packet and add it to hashmap
        long startTime = System.nanoTime();
        this.packetStartTime.put(seqnum, startTime);
        int trialNumber = this.packetTrialNum.get(seqnum);
        //System.out.println("Starting timer " + seqnum);
        //Init timer thread
        PacketTimeout tracker = new PacketTimeout(seqnum, trialNumber, startTime);
        //Schedule it
        ScheduledFuture<?> futureTracker = timeoutScheduler.schedule(tracker, this.timeoutTime, TimeUnit.NANOSECONDS);
        //Add it to the timeoutMap
        this.packetTimeoutMap.put(seqnum, futureTracker);
    }

    public void rdt_send(){
        
        //Send packets in current window size
        while(running){
            synchronized(lock){
                // if(nextseqnum == base ){System.out.println("Base is: " + base);}
                while(nextseqnum - base < windowSize && nextseqnum <= this.maxPackets && this.messageBuffer.containsKey(nextseqnum)){
                    if(this.rBuffer.containsKey(nextseqnum)){break;}
                    try { send_buf = this.messageBuffer.get(nextseqnum).getBytes("UTF-8");                                
                    } catch (UnsupportedEncodingException e) {  e.printStackTrace(); }
                    DatagramPacket packet = new DatagramPacket(send_buf, send_buf.length, this.ipAddress, this.port);
                    // System.out.println("Sending " + nextseqnum);
                    sendData(packet,nextseqnum);
                    this.sendCount++;
                    nextseqnum++;
                }
            }
            
        }
    }
    
    public synchronized void rdt_rcv(DatagramPacket ackPacket,long receiveTime){
        
        //Get ack number
        String raw_ack = ""; 
        try {
            raw_ack = new String(ackPacket.getData(),0,ackPacket.getLength(), "UTF-8");
        } 
        catch (UnsupportedEncodingException e) { e.printStackTrace(); }

        int acknum = Integer.parseInt(raw_ack.substring(0, raw_ack.indexOf("$")));
        this.rBuffer.put(acknum,ackPacket);
        
        
        if(packetTimeoutMap.containsKey(acknum)){
            // System.out.println(acknum + "in timeout map");
            packetTimeoutMap.get(acknum).cancel(true);
            // packetTimeoutMap.remove(acknum);
            long sendTime = packetStartTime.get(acknum);
            if(debug){
                System.out.println("Seq " + acknum + ": Time Generated:" + sendTime + " RTT: " +  (receiveTime - sendTime) + " Number of attempts : " + this.packetTrialNum.get(acknum));
            }

            RTTPacket.put(acknum, receiveTime - sendTime);
            ackCount++;
            RTT_avg = ((ackCount-1)*RTT_avg + receiveTime - sendTime)/(ackCount);
            if(base >= 10) this.timeoutTime = (long)(2*RTT_avg);
            // this.bufferSize--;
            if(acknum == this.maxPackets){running = false;}
        }

        if(base == acknum){
            // System.out.println("Before : " + base);
            while(rBuffer.containsKey(base)){
                base++;
            }
            // System.out.println("After : " + base);
            // System.out.println("New base is: " + base);
            nextseqnum = base;
        }
    
    }
    
    public synchronized void timeout(int seqnum, int trialNumber,long startTime){
        
        //Only retransmit this packet.
        // System.out.println("Timeout for : " + seqnum);
        trialNumber++;
        this.packetTrialNum.replace(seqnum,trialNumber);
        try {
            send_buf = this.messageBuffer.get(seqnum).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {}
        DatagramPacket packet = new DatagramPacket(send_buf, send_buf.length, this.ipAddress, this.port);
        sendData(packet, seqnum);
        this.sendCount++;
    }

    class PacketGenerator extends Thread{

        @Override
        public void run(){
            
            while(messageSeqNum <= maxPackets){
                //Generate a packet and add it to buffer.
                synchronized(lock2){
                    if(messageSeqNum <= maxPackets /* && bufferSize < maxBufferSize */){
                        double p = Math.random();
                        int len = 40 + (int)(p * (packetLength - 40));
                        String msg = createMessage(len,messageSeqNum);
                        messageBuffer.put(messageSeqNum,msg);
                        messageSeqNum ++;
                    }
                }
                try {
                    Thread.sleep((long)1000/packetGenRate);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            
               
        }
    }   
    
    class PacketTimeout implements Runnable{
        
        private int seqnum;
        private int trialNumber;
        private long startTime;

        public PacketTimeout(int seqnum, int trialNumber,long startTime){
            this.seqnum = seqnum;
            this.trialNumber = trialNumber;
            this.startTime = startTime;
        }
        
        @Override
        public void run(){
            // System.out.println("Timeout: " + seqnum + " trial number: "+trialNumber);
            //Call the timeout function with packetID and trialNumber
            if(rBuffer.containsKey(seqnum)){return;}
            if(trialNumber < 5){ 
                synchronized(lock){
                    timeout(seqnum,trialNumber,startTime);  
                }  
            }
            else{  running = false; }
        }
    }
    
    class ACKThread extends Thread{
        
        @Override
        public void run(){
            while(running){
                try {
                    DatagramPacket ackPacket = new DatagramPacket(ack_buf, ack_buf.length);
                    socket.receive(ackPacket);
                    long receiveTime = System.nanoTime();
                    //Process it
                    synchronized(lock){
                        rdt_rcv(ackPacket,receiveTime);
                        // System.out.println("Base is: (in ack)" + base); 
                        // System.out.println("Is it in message buffer? " + messageBuffer.containsKey(base));
                        // System.out.println("Is it in rBuffer? " + rBuffer.containsKey(base));
                        // System.out.println("Nextseqnum is: (in ack)" + nextseqnum);
                        // System.out.println("Timeout time is : (in ms)" + timeoutTime);
                    }
                    // synchronized(lock2){
                    //     // System.out.println("Exited ack lock: " + messageSeqNum);
                    // }
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (SocketException e){
                    //Socket closed.
                    continue;
                } catch (IOException e){
                    //Ideally not possible
                    e.printStackTrace();
                } 
            }
        }
    }

}