import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
public class SenderGBN{
    
    //Command line arguments    
    private boolean debug ;     //DEBUG flag
    private String address;     //IP Address
    private int port;           //Receiver port
    private int packetLength;   //Length of UDP Packet to be sent
    private int packetGenRate;  //Generation rate of packets
    private int maxPackets;     //Max number of packets to be sent
    private int windowSize;     //Size of window/Thread pool
    private int maxBufferSize;  //Size of Buffer
    
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
    ConcurrentHashMap<Integer,Integer> rBuffer ;
    ScheduledThreadPoolExecutor messageExecutor;
    ConcurrentHashMap<Integer,String> messageBuffer;
    volatile int bufferSize;
    volatile int numPacketsGenerated;
    volatile int messageSeqNum;
    
    volatile double RTT_avg;
    volatile int ackCount;
    volatile int sendCount;
    volatile long timeoutTime;
    volatile long progStartTime;

    //Lock to maintain mutual exclusion to variables
    private final Lock lock1 = new ReentrantLock(true);
    private final Lock lock2 = new ReentrantLock(true);

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
        
        //Process args from commad line
        for(int i=0;i<args.length;++i){
            if(args[i].equals("-d")){debug = true;}
            else if(args[i].equals("-s")){address = args[++i];}
            else if(args[i].equals("-p")){port = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-l")){packetLength = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-r")){packetGenRate = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-n")){maxPackets = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-w")){windowSize = Integer.parseInt(args[++i]);}
            else if(args[i].equals("-b")){maxBufferSize = Integer.parseInt(args[++i]);}
            else ;
        }
        
        SenderGBN client = new SenderGBN(debug, address, port, packetLength, packetGenRate, maxPackets, windowSize, maxBufferSize);

        // System.out.println("Setting up threads");
        client.setupThreads();
        client.rdt_send();
        // System.out.println("Exited main thread");
        client.cleanupThreads();
        
    }
    
    public SenderGBN(boolean debug, String address, int port, int packetLength, int packetGenRate, int maxPackets, int windowSize, int maxBufferSize){
        
        this.progStartTime = System.nanoTime();
        // Initialize command line arguments
        this.debug = debug;
        this.address = address;
        this.port = port;
        this.packetLength = packetLength;
        this.packetGenRate = packetGenRate;
        this.maxPackets = maxPackets;
        this.windowSize = windowSize;
        this.maxBufferSize = maxBufferSize;    
        this.sendCount = 0;
        //Get the ip address of the receiver
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
        this.rBuffer = new ConcurrentHashMap<Integer,Integer>();
        //Initialize trial number for packets
        for(int i = 1;i <= this.maxPackets; ++i){
            this.packetTrialNum.put(i,1);
        }

        this.RTTPacket = new ConcurrentHashMap<Integer,Long>();
        this.RTTPacket = new ConcurrentHashMap<>();

        this.RTT_avg = 0.0d;
        this.ackCount = 0;
        this.timeoutTime = 1000 * 1000000; //Initial value of timeout in nano seconds
        
        this.messageExecutor = new ScheduledThreadPoolExecutor(1);
        this.messageBuffer = new ConcurrentHashMap<Integer,String>();
        this.messageSeqNum = 1;
        this.bufferSize = 0;
        this.numPacketsGenerated = 0;

        //Start sending packets
        this.running = true;
    }

    public void setupThreads(){
        //Start ack thread
        ACKThread ackT = new ACKThread();
        ackT.start();

        //Start generating packets
        PacketGenerator gen = new PacketGenerator();
        this.messageExecutor.scheduleAtFixedRate(gen, 0,(long)(1000/this.packetGenRate) ,TimeUnit.MILLISECONDS );

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
        // System.out.println(msgSize);
        msgSize = msgSize/2;    
        StringBuilder sb = new StringBuilder(msgSize);
        for (int i=0; i<msgSize; i++) {
            sb.append('$');
        }
        String str = sb.toString();
        str = "" + seqnum + str;
        return str;
    }

    public synchronized void sendData(DatagramPacket packet,int seqnum){
        //Send packet to IP Address
        try {
            this.socket.send(packet);
        } catch (IOException e) {}
        sendCount++;
        //Start a timer for the packet and add it to hashmap
        long startTime = System.nanoTime();
        this.packetStartTime.put(seqnum, startTime);
        int trialNumber = this.packetTrialNum.get(seqnum);
        //System.out.println("Starting timer " + seqnum);

        //Init timer thread
        PacketTimeout tracker = new PacketTimeout(seqnum, trialNumber, startTime);
        //Schedule it
        // System.out.println("Sending #"+seqnum+" with timeout " + (this.timeoutTime/1e6)+" ms");
        ScheduledFuture<?> futureTracker = timeoutScheduler.schedule(tracker, (long)(this.timeoutTime/1e6), TimeUnit.MILLISECONDS);
        //Add it to the timeoutMap
        this.packetTimeoutMap.put(seqnum, futureTracker);
    }

    public synchronized void rdt_send(){

        //Send packets in current window size
        // System.out.println("In rdt_send");
        while(running){
            // System.out.println("Didnt get lock");
            lock1.lock();
                lock2.lock();
                    // System.out.println("Got lock");
                    if(nextseqnum - base < windowSize && nextseqnum<=this.maxPackets){
                        if(this.messageBuffer.containsKey(nextseqnum)){
                            try { send_buf = this.messageBuffer.get(nextseqnum).getBytes("UTF-8"); } 
                            catch (UnsupportedEncodingException e) { e.printStackTrace(); }
                            // System.out.println("Sending packet...");
                            DatagramPacket packet = new DatagramPacket(send_buf, send_buf.length, this.ipAddress, this.port);
                            sendData(packet,nextseqnum);
                            nextseqnum++;
                        }   
                    }
                lock2.unlock();
            lock1.unlock();
            // System.out.println("base: " + base + "nextseqnum: " +nextseqnum);
        }
    }
    
    public void rdt_rcv(DatagramPacket ackPacket,long receiveTime){
        
        //Get ack number
        String raw_ack = ""; 
        try { raw_ack = new String(ackPacket.getData(),0,ackPacket.getLength(), "UTF-8" ); } 
        catch (UnsupportedEncodingException e) { e.printStackTrace(); }

        int acknum = Integer.parseInt(raw_ack.substring(0, raw_ack.indexOf("$")));
        // System.out.println("Received ACK: " + acknum);
        rBuffer.put(acknum, acknum);
        //Cancel timer only corresponding to acknum
        long sendTime = packetStartTime.get(acknum);

        if(this.debug){
            long diff = receiveTime - sendTime;
            diff = diff/1000;
            long millirdt = diff/1000;;
            long micrordt = diff%1000;;
            diff = sendTime - progStartTime;
            diff = sendTime/1000;
            long microsend = diff%1000;
            long millisend = diff/1000;

            // microsend = microsend - millisend * 1000; 
            System.out.println("Seq " + acknum + ": Time Generated: " + millisend + ":" + microsend + " RTT: " + millirdt + ":" + micrordt + " Number of attempts: " + this.packetTrialNum.get(acknum));
        }

        if(packetTimeoutMap.containsKey(acknum)){
            
            //Interrupted cancel for timeout
            packetTimeoutMap.get(acknum).cancel(true);
            packetTimeoutMap.remove(acknum);
            
            //Update RTT
            RTTPacket.putIfAbsent(acknum, receiveTime - sendTime);
            ackCount++;
            RTT_avg = ((ackCount-1)*RTT_avg + receiveTime - sendTime)/(ackCount); //in nanoseconds

            if(base >= 10) this.timeoutTime = (long)(3*RTT_avg);
            
            //This is Go Back N
            
            base = acknum+1;

            //Remove acknum from message Buffer.
            if(acknum == this.maxPackets){running = false;}
        
        }
        else{
            //This is a repeated acknum - 
            //delete all timers from acknum+1 to nextseqnum and update base to acknum+1
            base = acknum+1;
            for(int i = 0;i<=this.windowSize;++i){
                ScheduledFuture<?> ftimer = this.packetTimeoutMap.get(i+base);
                if(ftimer!=null) ftimer.cancel(true);
                this.packetTimeoutMap.remove(i);
                
                // if(this.messageBuffer.containsKey(i)){
                //     this.messageBuffer.remove(i);
                // }
            }
            nextseqnum = base;
            // this.messageSeqNum = acknum;
        }
    }
    
    public void timeout(int seqnum, int trialNumber,long startTime){
        //Retransmit all unackd packets from this packet.
        // System.out.println("Inside timeout function");
        base = seqnum;
        // this.messageSeqNum = base;
        trialNumber++;
        for(int i = base;i <= nextseqnum;++i){
            ScheduledFuture<?> ftimer = this.packetTimeoutMap.get(i);
            if(ftimer!=null) ftimer.cancel(true);
            this.packetTimeoutMap.remove(i);
        }

        nextseqnum = base;
        this.packetTrialNum.replace(seqnum,trialNumber);
    }

    class PacketGenerator implements Runnable{

        @Override
        public void run(){
            //Generate a packet and add it to the message generator.
            // System.out.println("In packet generator");
            lock2.lock();
                if(messageSeqNum <= maxPackets){
                    // System.out.println("seq: " + messageSeqNum);
                    String msg = createMessage(packetLength,messageSeqNum);
                    messageBuffer.put(messageSeqNum,msg);
                    messageSeqNum ++;
                }
            lock2.unlock();
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
            // System.out.println("Timeout:# " + seqnum + " trial: "+trialNumber);
            if(!rBuffer.containsKey(seqnum))
            //Call the timeout function with packetID and trialNumber
            if(trialNumber < 5){ 
                lock1.lock();
                    // System.out.println("Acquired timeout lock");
                    timeout(seqnum,trialNumber,startTime);  
                    // System.out.println("Released timeout lock");
                lock1.unlock();
            }
            else{running = false;}
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
                    lock1.lock();
                        // System.out.println("Received ack");
                        rdt_rcv(ackPacket,receiveTime);
                    lock1.unlock();
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
            // System.out.println("Exiting ack thread");
        }
    }

}