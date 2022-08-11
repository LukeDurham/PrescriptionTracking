package node;

import node.blockchain.Block;
import node.blockchain.Transaction;
import node.communication.Address;
import node.communication.Message;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;

/**
 * Node represents a peer, a cooperating member within the network
 */
public class Node  {
    private ArrayList<Block> blockchain;
    private final int MAX_PEERS;
    private final int MIN_CONNECTIONS;
    private final Object lock;
    private final Address myAddress;
    private ServerSocket ss;
    private ArrayList<Address> localPeers;

    /* A collection of getters */
    public int getMaxPeers(){return this.MAX_PEERS;}
    public int getMinConnections(){return this.MIN_CONNECTIONS;}
    public Address getAddress(){return this.myAddress;}
    public ArrayList<Address> getLocalPeers(){return this.localPeers;}

    /**
     * Node constructor creates node and begins server socket to accept connections
     *
     * @param port Port
     * @param maxPeers Maximum amount of peer connections to maintain
     * @param initialConnections How many nodes we want to attempt to connect to on start
     */
    public Node(int port, int maxPeers, int initialConnections) {

        /* Initialize global variables */
        lock =  new Object();
        myAddress = new Address(port, "localhost");
        this.localPeers = new ArrayList<>();
        this.MIN_CONNECTIONS = initialConnections;
        this.MAX_PEERS = maxPeers;

        initializeBlockchain();

        try {
            ss = new ServerSocket(port);
            Acceptor acceptor = new Acceptor(this);
            acceptor.start();
            System.out.println("node.Node up and running on port " + port + " " + InetAddress.getLocalHost());
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    /**
     * Initializes blockchain
     */
    public void initializeBlockchain(){
        blockchain = new ArrayList<Block>();
        blockchain.add(new Block(new ArrayList<Transaction>(), "", 0));
    }

    public void gossipBlock(){
        // If block doesnt contain the signature of our peers, gossip to them ??
    }

    /**
     * Adds a block
     * @param block Block to add
     */
    public void addBlock(Block block){
        Block lastBlock = blockchain.get(blockchain.size() - 1);

        /* Verify block signatures */
        // Avoiding a memory fill attack

        /* Is the block newer than our chain */
        if(block.getBlockId() > lastBlock.getBlockId()){ //

            /* Is the block ahead of our expectation */
            if(block.getBlockId() > lastBlock.getBlockId() + 1){
                // Add to memory, seek or wait for the expected block

            }else{ // It is the block we expect
                // add block
                // gossip block
            }
        }else{
            // Do not add block
        }
    }

    /**
     * Determines if a connection is eligible
     * @param address Address to verify
     * @param connectIfEligible Connect to address if it is eligible
     * @return True if eligible, otherwise false
     */
    public boolean eligibleConnection(Address address, boolean connectIfEligible){
        synchronized(lock) {
            if (localPeers.size() < MAX_PEERS - 1 && (!address.equals(this.getAddress()) && !this.containsAddress(localPeers, address))) {
                if(connectIfEligible){
                    establishConnection(address);
                }
                return true;
            }
            return false;
        }
    }

    /**
     * Add a connection to our dynamic list of peers to speak with
     * @param address
     */
    public void establishConnection(Address address){
        localPeers.add(address);
        System.out.println("Node " + this.getAddress().getPort() + ": Added peer: " + address.getPort());
    }

    /**
     * Iterate through a list of peers and attempt to establish a mutual connection
     * with a specified amount of nodes
     * @param globalPeers
     */
    public void requestConnections(ArrayList<Address> globalPeers){
        try {
            if(globalPeers.size() > 0){
                /* Begin seeking connections */
                ClientConnection connect = new ClientConnection(this, globalPeers);
                connect.start();

                /* Begin heartbeat monitor */
                Thread.sleep(10000);
                HeartBeatMonitor heartBeatMonitor = new HeartBeatMonitor(this);
                heartBeatMonitor.start();
            }
        } catch (SocketException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns true if the provided address is in the list, otherwise false
     * @param list
     * @param address
     * @return
     */
    public boolean containsAddress(ArrayList<Address> list, Address address){
        for (Address existingAddress : list) {
            if (existingAddress.equals(address)) {
                return true;
            }
        }
        return false;
    }

    public Address removeAddress(Address address){
        for (Address existingAddress : localPeers) {
            if (existingAddress.equals(address)) {
                localPeers.remove(address);
                return address;
            }
        }
        return null;
    }

    /**
     * Acceptor is a thread responsible for maintaining the server socket by
     * accepting incoming connection requests, and starting a new ServerConnection
     * thread for each request. Requests terminate in a finite amount of steps, so
     * threads return upon completion.
     */
    class Acceptor extends Thread {
        Node node;

        Acceptor(Node node){
            this.node = node;
        }

        public void run() {
            Socket client;
            while (true) {
                try {
                    client = ss.accept();
                    new ServerConnection(client, node).start();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * HeartBeatMonitor is a thread which will periodically 'ping' nodes which this node is connected to.
     * It expects a 'ping' back. Upon receiving the expected reply the other node is deemed healthy.
     *
     */
    class HeartBeatMonitor extends Thread {
        Node node;

        HeartBeatMonitor(Node node){
            this.node = node;
        }

        public void run() {
            while (true) {
                for(Address address : localPeers){
                    try {
                        Thread.sleep(5000);
                        Socket s = new Socket("localhost", address.getPort());
                        InputStream in = s.getInputStream();
                        ObjectInputStream oin = new ObjectInputStream(in);
                        OutputStream out = s.getOutputStream();
                        ObjectOutputStream oout = new ObjectOutputStream(out);
                        Message message = new Message(Message.Request.PING);
                        oout.writeObject(message);
                        oout.flush();
                        Message messageReceived = (Message) oin.readObject();
                        if(messageReceived.getRequest().name().equals("PING")){
                            System.out.println("Node " + node.getAddress().getPort() + ": Node " + localPeers.get(0).getPort() + " pinged back");
                        }else{
                            System.out.println("Node " + node.getAddress().getPort() + ": Node " + localPeers.get(0).getPort() + " idk :(");
                        }
                        s.close();
                    } catch (IOException e) {
                        System.out.println("Received IO Exception from node " + address.getPort());
                        removeAddress(address);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        System.out.println("Received Interrupted Exception from node " + address.getPort());
                        throw new RuntimeException(e);
                    } catch (ConcurrentModificationException e){
                        break;
                    }
                }
            }
        }
    }
}

