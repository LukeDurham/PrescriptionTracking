package node;

import static node.communication.utils.DSA.generateDSAKeyPair;
import static node.communication.utils.DSA.signHash;
import static node.communication.utils.DSA.verifySignatureFromRegistry;
import static node.communication.utils.DSA.writePubKeyToRegistry;
import static node.communication.utils.Hashing.getBlockHash;
import static node.communication.utils.Hashing.getSHAString;
import static node.communication.utils.Utils.chainString;
import static node.communication.utils.Utils.containsAddress;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import node.blockchain.Block;
import node.blockchain.BlockSkeleton;
import node.blockchain.Transaction;
import node.blockchain.TransactionValidator;
import node.blockchain.merkletree.MerkleTree;
import node.blockchain.prescription.PtBlock;
import node.blockchain.prescription.PtTransaction;
import node.blockchain.prescription.PtTransactionValidator;
import node.blockchain.prescription.ValidationResult;
import node.blockchain.prescription.Events.Algorithm;
import node.communication.Address;
import node.communication.BlockSignature;
import node.communication.ClientConnection;
import node.communication.ServerConnection;
import node.communication.ValidationResultSignature;
import node.communication.messaging.Message;
import node.communication.messaging.Message.Request;
import node.communication.messaging.Messager;
import node.communication.messaging.MessagerPack;
import node.communication.utils.DSA;
import node.communication.utils.Hashing;
import node.communication.utils.Utils;


/**
 * A Node represents a peer, a cooperating member within a network following this Quorum-based blockchain protocol
 * as implemented here.
 *
 * This node participates in a distributed and decentralized network protocol, and achieves this by using some of
 * the following architecture features:
 *
 *      Quorum Consensus
 *      DSA authentication
 *      Blockchain using SHA-256
 *      Multithreading
 *      Servant Model
 *      Stateful Model
 *      TCP/IP communication
 *
 *
 * Beware, any methods below are a WIP
 */
public class Node  {
    private long startTimeBlockCon;
    private long startTimeQuorumAns;
    private long endTimeBlockCon;
    private long endtimeQuorumAns;
    private int count = 0;

    /**
     * Node constructor creates node and begins server socket to accept connections
     *
     * @param port               Port
     * @param maxPeers           Maximum amount of peer connections to maintain
     * @param initialConnections How many nodes we want to attempt to connect to on start
     */
    public Node(NodeType nodeType, String use, int port, int maxPeers, int initialConnections, int numNodes, int quorumSize, int minimumTransaction, int debugLevel) {
        
        this.nodeType = nodeType;

        /* Prescription Tracking */
        if(nodeType.name().equals("Doctor")){
            Random random = new Random();
            algorithmSeed = random.nextInt(100);
            
        }
        algorithms = new ArrayList<>();


        /* Configurations */
        USE = use;
        MIN_CONNECTIONS = initialConnections;
        MAX_PEERS = maxPeers;
        NUM_NODES = numNodes;
        QUORUM_SIZE = quorumSize;
        DEBUG_LEVEL = debugLevel;
        MINIMUM_TRANSACTIONS = minimumTransaction;

        /* Locks for Multithreading */
        lock =  new Object();
        quorumLock = new Object();
        quorumReadyVotesLock = new Object();
        memPoolRoundsLock = new Object();
        sigRoundsLock = new Object();
        accountsLock = new Object();
        memPoolLock = new Object();
        blockLock = new Object();

        /* Multithreaded Counters for Stateful Servant */
        memPoolRounds = 0;
        quorumReadyVotes = 0;
        state = 0;

        InetAddress ip;

        try {
            ip = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        String host = ip.getHostAddress();

        /* Other Data for Stateful Servant */
        myAddress = new Address(port, host, nodeType);
        localPeers = new ArrayList<>();
        ptClients = new ArrayList<>();
        mempool = new HashMap<>();
        accountsToAlert = new HashMap<>();
        

        /* Public-Private (DSA) Keys*/
        KeyPair keys = generateDSAKeyPair();
        privateKey = keys.getPrivate();
        writePubKeyToRegistry(myAddress, keys.getPublic());

        /* Begin Server Socket */
        try {
            ss = new ServerSocket(port);
            Acceptor acceptor = new Acceptor(this);
            acceptor.start();
            System.out.println("Node up and running on port " + port + " " + InetAddress.getLocalHost());
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    /* A collection of getters */
    public int getMaxPeers(){return this.MAX_PEERS;}
    public int getMinConnections(){return this.MIN_CONNECTIONS;}
    public Address getAddress(){return this.myAddress;}
    public ArrayList<Address> getLocalPeers(){return this.localPeers;}
    public HashMap<String, Transaction> getMempool(){return this.mempool;}
    public LinkedList<Block> getBlockchain(){return blockchain;}

    /**
     * Initializes blockchain
     */
    public void initializeBlockchain(){
        blockchain = new LinkedList<Block>();

        addBlock(new PtBlock(new HashMap<String, Transaction>(), "000000", 0, null));
        
    }

    /**
     * Determines if a connection is eligible
     * @param address Address to verify
     * @param connectIfEligible Connect to address if it is eligible
     * @return True if eligible, otherwise false
     */
    public boolean eligibleConnection(Address address, boolean connectIfEligible){
        synchronized(lock) {
            if (localPeers.size() < MAX_PEERS - 1 && (!address.equals(this.getAddress()) && !containsAddress(localPeers, address))) {
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
        synchronized (lock){
            localPeers.add(address);
        }
    }

    /**
     * Iterate through a list of peers and attempt to establish a mutual connection
     * with a specified amount of nodes
     * @param globalPeers
     */
    public void requestConnections(ArrayList<ArrayList<Address>> globalPeers){
        try {
            this.globalPeers = globalPeers;
            int globalPeersCount = 0;
            for (ArrayList<Address> arrayList: globalPeers)
            {
                if (arrayList.size() > 0)
                {
                    globalPeersCount++;
                    break;
                }
            }
            if(globalPeersCount > 0){
                /* Begin seeking connections */
                ClientConnection connect = new ClientConnection(this, globalPeers);
                connect.start();

                /* Begin heartbeat monitor */
                Thread.sleep(10000);
                // HeartBeatMonitor heartBeatMonitor = new HeartBeatMonitor(this);
                // heartBeatMonitor.start();

                /* Begin protocol */
                initializeBlockchain();
            }
        } catch (SocketException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Address removeAddress(Address address){
        synchronized (lock){
            for (Address existingAddress : localPeers) {
                if (existingAddress.equals(address)) {
                    localPeers.remove(address);
                    return address;
                }
            }
            return null;
        }
    }

    public void gossipTransaction(Transaction transaction){
        synchronized (lock){
            for(Address address : localPeers){
                Messager.sendOneWayMessage(address, new Message(Message.Request.ADD_TRANSACTION, transaction), myAddress);
            }
        }
    }

    public void addTransaction(Transaction transaction){
        while(state != 0){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        verifyTransaction(transaction);
    }


    public void verifyTransaction(Transaction transaction){
        synchronized(memPoolLock){
            if(Utils.containsTransactionInMap(transaction, mempool)) return;

            if(DEBUG_LEVEL == 1){System.out.println("Node " + myAddress.getPort() + ": verifyTransaction: " + transaction.getUID() + ", blockchain size: " + blockchain.size());}

            LinkedList<Block> clonedBlockchain = new LinkedList<>();

            if(blockchain == null) System.out.println("blockchain is null :(");
            clonedBlockchain.addAll(blockchain);
            for(Block block : clonedBlockchain){
                if(block.getTxList().containsKey(getSHAString(transaction.getUID()))){
                    // We have this transaction in a block
                    if(DEBUG_LEVEL == 1){System.out.println("Node " + myAddress.getPort() + ": trans :" + transaction.getUID() + " found in prev block " + block.getBlockId());}
                    return;
                }
            }

            TransactionValidator tv;
            Object[] validatorObjects = new Object[3];

            
                tv = new PtTransactionValidator(); // To be changed to another use case in the future
                // PtTransaction transaction = (PtTransaction) objects[0];
                validatorObjects[0] = transaction;
            

            if(!tv.validate(validatorObjects)){
                if(DEBUG_LEVEL == 1){System.out.println("Node " + myAddress.getPort() + "Transaction not valid");}
                return;
            }

            mempool.put(getSHAString(transaction.getUID()), transaction);
            gossipTransaction(transaction);

            if(DEBUG_LEVEL == 1){System.out.println("Node " + myAddress.getPort() + ": Added transaction. MP:" + mempool.values());}
        }         
    }

    //Reconcile blocks
    public void sendQuorumReady(){
        //state = 1;
        stateChangeRequest(1);
        quorumSigs = new ArrayList<>();
        Block currentBlock = blockchain.getLast();
        

        if(DEBUG_LEVEL == 1) System.out.println("Node " + myAddress.getPort() + " sent quorum is ready for q: " + quorum);

        for(Address quorumAddress : quorum){
            if(!myAddress.equals(quorumAddress)) {
                try {
                    Thread.sleep(2000);
                    MessagerPack mp = Messager.sendInterestingMessage(quorumAddress, new Message(Message.Request.QUORUM_READY), myAddress);
                    Message messageReceived = mp.getMessage();
                    Message reply = new Message(Message.Request.PING);

                    if(messageReceived.getRequest().name().equals("RECONCILE_BLOCK")){
                        Object[] blockData = (Object[]) messageReceived.getMetadata();
                        int blockId = (Integer) blockData[0];
                        String blockHash = (String) blockData[1];

                        if(blockId == currentBlock.getBlockId()){

                        }else if (blockId < currentBlock.getBlockId()){
                            // tell them they are behind
                            reply = new Message(Message.Request.RECONCILE_BLOCK, currentBlock.getBlockId());
                            if(DEBUG_LEVEL == 1) {
                                System.out.println("Node " + myAddress.getPort() + ": sendQuorumReady RECONCILE");
                            }
                        }else if (blockId > currentBlock.getBlockId()){
                            // we are behind, quorum already happened / failed
                            reply = new Message(Message.Request.PING);
                            //blockCatchUp();

                        }
                        mp.getOout().writeObject(reply);
                        mp.getOout().flush();
                    }

                    mp.getSocket().close();
                } catch (IOException e) {
                    System.out.println("Node " + myAddress.getPort() + ": sendQuorumReady Received IO Exception from node " + quorumAddress.getPort());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    //Reconcile blocks
    public void receiveQuorumReady(ObjectOutputStream oout, ObjectInputStream oin){
        synchronized (quorumReadyVotesLock){
            while(state != 1){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Block currentBlock = blockchain.getLast();
            

            if(DEBUG_LEVEL == 1) System.out.println("Node " + myAddress.getPort() + ": receiveQuorumReady invoked for " + quorum );

            try {

                if(!inQuorum()){
                    if(DEBUG_LEVEL == 1) {
                        System.out.println("Node " + myAddress.getPort() + ": not in quorum? q: " + quorum + " my addr: " + myAddress);
                    }
                    oout.writeObject(new Message(Message.Request.RECONCILE_BLOCK, new Object[]{currentBlock.getBlockId(), getBlockHash(currentBlock, 0)}));
                    oout.flush();
                    Message reply = (Message) oin.readObject();

                    if(reply.getRequest().name().equals("RECONCILE_BLOCK")){
                        //blockCatchUp();
                    }
                }else{
                    oout.writeObject(new Message(Message.Request.PING));
                    oout.flush();
                    quorumReadyVotes++;
                    if(quorumReadyVotes == quorum.size() - 1){
                        quorumReadyVotes = 0;
                        sendMempoolHashes();
                    }

                }
            } catch (IOException e) {
                System.out.println("Node " + myAddress.getPort() + ": receiveQuorumReady EOF");
                throw new RuntimeException(e);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    //AARON added a new parameter
    public void calculateEligibity(String hash, ObjectOutputStream oout, ObjectInputStream oin, int shard){
        synchronized (memPoolLock){
            Algorithm algo = new Algorithm(algorithmSeed);
            Boolean eligible = algo.runAlgorithm((PtTransaction)mempool.get(hash), shard);
            ValidationResult vr = new ValidationResult(eligible, algorithmSeed, hash);
            byte[] sig = DSA.signHash(vr.getStringForSig(), privateKey);
            
            ValidationResultSignature vrs = new ValidationResultSignature(sig, myAddress, vr);

            try {
                oout.writeObject(new Message(Request.CALCULATION_COMPLETE, vrs));
                oout.flush();
                System.out.println("Node " +myAddress.getPort() + ": Sent calculation result " + vr.isValid());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendMempoolHashes() {
        synchronized (memPoolLock){
            stateChangeRequest(2);            
            
            HashSet<String> keys = new HashSet<String>(mempool.keySet());
            Boolean foundAPatient = false;
            System.out.println("Node: " + myAddress.getPort() + "(Doctor): Sending mempool");
            for(String hash : keys){
                System.out.println("Node: " + myAddress.getPort() + "(Doctor): Processing keys");

                PtTransaction ptTransaction = (PtTransaction) mempool.get(hash);

                if(ptTransaction.getEvent().getAction().name().equals("Prescription")){
                    ArrayList<ValidationResultSignature> vrs = new ArrayList<>();
                    this.startTimeQuorumAns = System.nanoTime();
                    this.startTimeBlockCon = System.nanoTime(); // start the timer
                    //for each quorum member...
                    for (Address quorumMeber: quorum)
                    {
                        //Consensus randomization generator for number of shards
                        String memberString = getSHAString(quorumMeber.toString() + hash);
                        BigInteger bigInt = new BigInteger(memberString, 16); // Converts the memberString in to a big Int
                        bigInt = bigInt.mod(BigInteger.valueOf(NUM_NODES)); // we mod the big int I guess
                        int seed = bigInt.intValue(); // This makes our seed
                        Random rand = new Random(seed); //seed needed to have consensus among how many shards a doctor wants
                    
                        int shards = rand.nextInt(allAlgorithms.length -1) + 1; //generate a random amount of shards
                        System.out.println("AMOUNT OF SHARDS DOCTOR WANTS TO RUN:" + shards);


                        
                        //Randomize the index array of all algorithms
                        List<Integer> shardList = Arrays.asList(allAlgorithms);
                        Collections.shuffle(shardList);
                        shardList.toArray(allAlgorithms);
                        System.out.println("Shard List: " + Arrays.toString(allAlgorithms));

                        //Generate the array with the desired patients to solve this algorithm, lets worry about indexing the same patient later
                        Address[] patients = new Address[shards];
                        for (int i = 0; i < shards; i++)
                        {
                            foundAPatient = true;
                            int randval = rand.nextInt(globalPeers.get(2).size()); //get a random value from 0 to length of patient subarraylist
                            patients[i] = globalPeers.get(2).get(randval); //set the array index to that patient
                        }
                        
                        //assign them and make sure to loop
                        for (int k = 0; k < shards; k++)
                        {
                            //the shardNumber we run will be the first index in the randomized array
                            shardNumber = allAlgorithms[k];
                            //shard out request to the patient k for shard k
                            System.out.println("Node: " + myAddress.getPort() + "(Doctor): About to send out calc request to patient " + (k + 1));
                            Message reply = Messager.sendTwoWayMessage(patients[k], new Message(Request.REQUEST_CALCULATION, hash), myAddress);
                            System.out.println("Node: " + myAddress.getPort() + "(Doctor): Sent out calc request to patient " + (k + 1));

                            if(reply.getRequest().name().equals("CALCULATION_COMPLETE")){
                                ValidationResultSignature vr = (ValidationResultSignature) reply.getMetadata();
                                vrs.add(vr);
                                System.out.println("Node: " + myAddress.getPort() + "(Doctor): Added vr to vrs from patient " + (k + 1));
                                System.out.println("Completed calculation for shard number : "  + shardNumber);
                            }


                            //if this isn't the last shard in the array....
                            if (k + 1 != shards)
                            {
                                //shard out request to second patient for shard k
                                System.out.println("Node: " + myAddress.getPort() + "(Doctor): About to send out calc request to patient " + (k + 2));
                                Message reply2 = Messager.sendTwoWayMessage(patients[k + 1], new Message(Request.REQUEST_CALCULATION, hash), myAddress);
                                System.out.println("Node: " + myAddress.getPort() + "(Doctor): Sent out calc request to patient " + (k  + 2));
                                if(reply2.getRequest().name().equals("CALCULATION_COMPLETE")){
                                    ValidationResultSignature vr2 = (ValidationResultSignature) reply.getMetadata();
                                    vrs.add(vr2);
                                    System.out.println("Node: " + myAddress.getPort() + "(Doctor): Added vr to vrs from patient " + (k + 2));
                                    System.out.println("Completed calculation for shard number : "  + shardNumber);
                                }
                            }
                        
                            //This is the last shard in the array....
                            else{
                                    //shard out request to very first patient for shard k ... (LOOP)
                                    System.out.println("LOOPING...");
                                    System.out.println("Node: " + myAddress.getPort() + "(Doctor): About to send out calc request to patient 1");
                                    Message replyLoop = Messager.sendTwoWayMessage(patients[0], new Message(Request.REQUEST_CALCULATION, hash), myAddress);
                                    System.out.println("Node: " + myAddress.getPort() + "(Doctor): Sent out calc request to patient 1");
                                    if(replyLoop.getRequest().name().equals("CALCULATION_COMPLETE")){
                                        ValidationResultSignature vr2 = (ValidationResultSignature) replyLoop.getMetadata();
                                        vrs.add(vr2);
                                        System.out.println("Node: " + myAddress.getPort() + "(Doctor): Added vr to vrs from patient");
                                        System.out.println("Completed calculation for shard number : "  + shardNumber + " and final shard finished.");
                                    }
                                }
                            
                        }
                        System.out.println("FINISHED ALGORITHM CALCULATION FOR DOCTOR ->: " + quorumMeber);
                    }


                    if(!foundAPatient)System.out.println("Node: " + myAddress.getPort() + "(Doctor): never found a patient");
                    ptTransaction.setValidationResultSignatures(vrs);
                }   
            }
            count++; 
            this.endtimeQuorumAns = System.nanoTime();
        
            long elapsedTimeQuorum = this.endtimeQuorumAns - this.startTimeQuorumAns; // get the elapsed time in nanoseconds
            double secondsQuorum = (double) elapsedTimeQuorum / 1_000_000_000.0; // convert to seconds
            System.out.println("Q_TIME 2:" + secondsQuorum);

                
            
            if(DEBUG_LEVEL == 1) System.out.println("Node " + myAddress.getPort() + ": sendMempoolHashes invoked");
            
                
            
            for (Address quorumAddress : quorum) {
                if (!myAddress.equals(quorumAddress)) {
                    try {
                        MessagerPack mp = Messager.sendInterestingMessage(quorumAddress, new Message(Message.Request.RECEIVE_MEMPOOL, keys), myAddress);                        ;
                        Message messageReceived = mp.getMessage();
                        if(messageReceived.getRequest().name().equals("REQUEST_TRANSACTION")){
                            ArrayList<String> hashesRequested = (ArrayList<String>) messageReceived.getMetadata();
                            if(DEBUG_LEVEL == 1) System.out.println("Node " + myAddress.getPort() + ": sendMempoolHashes: requested trans: " + hashesRequested);
                            ArrayList<Transaction> transactionsToSend = new ArrayList<>();
                            for(String hash2 : keys){
                                if(mempool.containsKey(hash2)){
                                    transactionsToSend.add(mempool.get(hash2));
                                }else{
                                    if(DEBUG_LEVEL == 1) System.out.println("Node " + myAddress.getPort() + ": sendMempoolHashes: requested trans not in mempool. MP: " + mempool);
                                }
                            }
                            mp.getOout().writeObject(new Message(Message.Request.RECEIVE_MEMPOOL, transactionsToSend));
                        }
                        mp.getSocket().close();
                    } catch (IOException e) {
                        System.out.println(e);
                    } catch (Exception e){
                        System.out.println(e);
                    }
                }
            }
        }
    }
    
    //AARON add this getter to use it in another class
    public int getShard()
    {
        return shardNumber;
    }

    public void receiveMempool(Set<String> keys, ObjectOutputStream oout, ObjectInputStream oin) {
        while(state != 2){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        resolveMempool(keys, oout, oin);
    }


    public void resolveMempool(Set<String> keys, ObjectOutputStream oout, ObjectInputStream oin) {
        synchronized(memPoolRoundsLock){
            if(DEBUG_LEVEL == 1) System.out.println("Node " + myAddress.getPort() + ": receiveMempool invoked"); 
            ArrayList<String> keysAbsent = new ArrayList<>();
            for (String key : keys) {
                if (!mempool.containsKey(key)) {
                    keysAbsent.add(key);
                }
            }
            try {
                if (keysAbsent.isEmpty()) {
                    oout.writeObject(new Message(Message.Request.PING));
                    oout.flush();
                } else {
                    if(DEBUG_LEVEL == 1) {System.out.println("Node " + myAddress.getPort() + ": receiveMempool requesting transactions for: " + keysAbsent); }
                    oout.writeObject(new Message(Message.Request.REQUEST_TRANSACTION, keysAbsent));
                    oout.flush();
                    Message message = (Message) oin.readObject();
                    ArrayList<Transaction> transactionsReturned = (ArrayList<Transaction>) message.getMetadata();
                    
                    for(Transaction transaction : transactionsReturned){

                        // Verify DSA sig for patient

                        // PtTransaction ptTransaction = (PtTransaction) transaction;
                        // ptTransaction.getValidationResultSignatures();

                        mempool.put(getSHAString(transaction.getUID()), transaction);
                        if(DEBUG_LEVEL == 1) System.out.println("Node " + myAddress.getPort() + ": recieved transactions: " + keysAbsent);
                    }
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                System.out.println(e);
                throw new RuntimeException(e);
            }

            memPoolRounds++;
            if(DEBUG_LEVEL == 1) System.out.println("Node " + myAddress.getPort() + ": receiveMempool invoked: mempoolRounds: " + memPoolRounds); 
            if(memPoolRounds == quorum.size() - 1){
                memPoolRounds = 0;
                constructBlock();
            }
        }
    }

    public int counter = 0;

    public void constructBlock(){
        synchronized(memPoolLock){
            if(DEBUG_LEVEL == 1) System.out.println("Node " + myAddress.getPort() + ": constructBlock invoked");
            stateChangeRequest(3);
            
            /* Make sure compiled transactions don't conflict */
            HashMap<String, Transaction> blockTransactions = new HashMap<>();

            TransactionValidator tv = null;
            
                // Room to enable another use case 
                tv = new PtTransactionValidator();
            
            
            for(String key : mempool.keySet()){
                Transaction transaction = mempool.get(key);
                Object[] validatorObjects = new Object[3];
                
                    // Validator objects will change according to another use case
                    validatorObjects[0] = (PtTransaction) transaction;
                
                tv.validate(validatorObjects);
                blockTransactions.put(key, transaction);
            }

            try {
                
                    HashMap<String, ArrayList<ValidationResultSignature>> answerSigs = new HashMap<>();

                    for(String hash : blockTransactions.keySet()){
                        PtTransaction ptTransaction = (PtTransaction) blockTransactions.get(hash);
                        answerSigs.put(hash, ptTransaction.getValidationResultSignatures());
                    }

                    // Room to enable another use case 
                    quorumBlock = new PtBlock(blockTransactions,
                        getBlockHash(blockchain.getLast(), 0),
                                blockchain.size(), answerSigs);
                    this.endTimeBlockCon = System.nanoTime(); // end the timer
                    long elapsedTime = this.endTimeBlockCon - this.startTimeBlockCon; // get the elapsed time in nanoseconds
                    double seconds = (double) elapsedTime / 1_000_000_000.0; // convert to seconds
                    System.out.println("Elapsed time for bc " + seconds + " seconds");
                    setQuorumTime(seconds);
                    

            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
  

            sendSigOfBlockHash();
        }
    }

    public void setQuorumTime(double seconds)
    {
        quorumConsensusTime = seconds;
        return;
    }
    public static double getQuorumTime()
    {
        return quorumConsensusTime;
    }

   

    public void sendSigOfBlockHash(){
        String blockHash;
        byte[] sig;

        try {blockHash = getBlockHash(quorumBlock, 0);
            sig = signHash(blockHash, privateKey);
        } catch (NoSuchAlgorithmException e) {throw new RuntimeException(e);}

        BlockSignature blockSignature = new BlockSignature(sig, blockHash, myAddress);
        sendOneWayMessageQuorum(new Message(Message.Request.RECEIVE_SIGNATURE, blockSignature));

        if(DEBUG_LEVEL == 1) {System.out.println("Node " + myAddress.getPort() + ": sendSigOfBlockHash invoked for hash: " + blockHash.substring(0, 4));}
    }

    public void receiveQuorumSignature(BlockSignature signature){
        synchronized (sigRoundsLock){
            if(DEBUG_LEVEL == 1) { System.out.println("Node " + myAddress.getPort() + ": 1st part receiveQuorumSignature invoked. state: " + state);}

            while(state != 3){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }


            if(!containsAddress(quorum, signature.getAddress())){
                if(DEBUG_LEVEL == 1) System.out.println("Node " + myAddress.getPort() + ": false sig from " + signature.getAddress());
                return;
            }

            if(!inQuorum()){
                if(DEBUG_LEVEL == 1) System.out.println("Node " + myAddress.getPort() + ": not in quorum? q: " + quorum + " my addr: " + myAddress); 
                return;
            } 

            quorumSigs.add(signature);
            int blockId = blockchain.size() - 1;

            if(DEBUG_LEVEL == 1) {
                System.out.println("Node " + myAddress.getPort() + ": receiveQuorumSignature invoked from " + 
                signature.getAddress().toString() + " qSigs: " + quorumSigs + " quorum: " + quorum + " block " + quorumBlock.getBlockId());
            }

            if(quorumSigs.size() == quorum.size() - 1){
                if(!inQuorum()){
                    if(DEBUG_LEVEL == 1) {
                        System.out.println("Node " + myAddress.getPort() + ": not in quorum? q: " + quorum + " my addr: " + myAddress);
                    }
                    System.out.println("Node " + myAddress.getPort() + ": rQs: not in quorum? q: " + quorum + " my addr: " + myAddress + " block: " + blockId);
                    return;
                }
                tallyQuorumSigs();
            }
        }
    }

    public void tallyQuorumSigs(){
        synchronized (blockLock) {
            resetMempool();

            if (DEBUG_LEVEL == 1) {System.out.println("Node " + myAddress.getPort() + ": tallyQuorumSigs invoked");}

            //state = 4;
            stateChangeRequest(4);
            if(!inQuorum()){
                System.out.println("Node " + myAddress.getPort() + ": tQs: not in quorum? q: " + quorum + " my addr: " + myAddress);
                return;
            }

            HashMap<String, Integer> hashVotes = new HashMap<>();
            String quorumBlockHash;
            int block = blockchain.size() - 1;
            try {                
                if(quorumBlock == null){
                    System.out.println("Node " + myAddress.getPort() + ": tallyQuorumSigs quorum null");
                }

                quorumBlockHash = getBlockHash(quorumBlock, 0);
                hashVotes.put(quorumBlockHash, 1);;
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            for (BlockSignature sig : quorumSigs) {
                if (verifySignatureFromRegistry(sig.getHash(), sig.getSignature(), sig.getAddress())) {
                    if (hashVotes.containsKey(sig.getHash())) {
                        int votes = hashVotes.get(sig.getHash());
                        votes++;
                        hashVotes.put(sig.getHash(), votes);
                    } else {
                        hashVotes.put(sig.getHash(), 0);
                    }
                } else {
                    /* Signature has failed. Authenticity or integrity compromised */
                }


            }

            String winningHash = quorumSigs.get(0).getHash();

            for (BlockSignature blockSignature : quorumSigs) {
                String hash = blockSignature.getHash();
                if (hashVotes.get(hash) != null && (hashVotes.get(hash) > hashVotes.get(winningHash))) {
                    winningHash = hash;
                }
            }
            if (DEBUG_LEVEL == 1) {
                System.out.println("Node " + myAddress.getPort() + ": tallyQuorumSigs: Winning hash votes = " + hashVotes.get(winningHash));
            }
            if (hashVotes.get(winningHash) == quorum.size()) {
                if (quorumBlockHash.equals(winningHash)) {
                    sendSkeleton();
                    addBlock(quorumBlock);
                    if(quorumBlock == null){
                        System.out.println("Node " + myAddress.getPort() + ": tallyQuorumSigs quorum null");

                    }                    
                } else {
                    System.out.println("Node " + myAddress.getPort() + ": tallyQuorumSigs: quorumBlockHash does not equals(winningHash)");
                }
            } else {
                System.out.println("Node " + myAddress.getPort() + ": tallyQuorumSigs: failed vote. votes: " + hashVotes + " my block " + quorumBlock.getBlockId() + " " + quorumBlockHash.substring(0, 4) +
                " quorumSigs: " + quorumSigs);
            } 
            hashVotes.clear();
            quorumSigs.clear();
        }
    }

    private void resetMempool(){
        synchronized(memPoolLock){
            mempool = new HashMap<>();
        }
    }

    public void sendSkeleton(){
        synchronized (lock){
            //state = 0;
            ArrayList<ArrayList<ValidationResultSignature>> vrs = new ArrayList<>();

            for(String hash : quorumBlock.getTxList().keySet()){
                PtTransaction transaction = (PtTransaction) quorumBlock.getTxList().get(hash);
                if(!transaction.getEvent().getAction().name().equals("Algorithm"))vrs.add(transaction.getValidationResultSignatures());
            }

            if(DEBUG_LEVEL == 1) {
                System.out.println("Node " + myAddress.getPort() + ": sendSkeleton invoked. qSigs " + quorumSigs);
            }
            BlockSkeleton skeleton = null;
            try {
                if(quorumBlock == null){
                    System.out.println("Node " + myAddress.getPort() + ": sendSkeleton quorum null");

                }
                skeleton = new BlockSkeleton(quorumBlock.getBlockId(),
                        new ArrayList<String>(quorumBlock.getTxList().keySet()), quorumSigs, getBlockHash(quorumBlock, 0), vrs);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }

            for(Address address : localPeers){
                Messager.sendOneWayMessage(address, new Message(Message.Request.RECEIVE_SKELETON, skeleton), myAddress);
            }

        }
    }

    public void sendSkeleton(BlockSkeleton skeleton){
        synchronized (lock){
            if(DEBUG_LEVEL == 1) {
                System.out.println("Node " + myAddress.getPort() + ": sendSkeleton(local) invoked: BlockID " + skeleton.getBlockId());
            }
            for(Address address : localPeers){
                if(!address.equals(myAddress)){
                    Messager.sendOneWayMessage(address, new Message(Message.Request.RECEIVE_SKELETON, skeleton), myAddress);
                }
            }
        }
    }

    public void receiveSkeleton(BlockSkeleton blockSkeleton){
        while(state != 0){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        validateSkeleton(blockSkeleton);
    }

    public void validateSkeleton(BlockSkeleton blockSkeleton){
        synchronized (blockLock){
            Block currentBlock = blockchain.getLast();

            if(currentBlock.getBlockId() + 1 != blockSkeleton.getBlockId()){
                //if(DEBUG_LEVEL == 1) { System.out.println("Node " + myAddress.getPort() + ": receiveSkeleton(local) currentblock not synced with skeleton. current id: " + currentBlock.getBlockId() + " new: " + blockSkeleton.getBlockId()); }
                return;
            }else{
                if(DEBUG_LEVEL == 1) { System.out.println("Node " + myAddress.getPort() + ": receiveSkeleton(local) invoked. Hash: " + blockSkeleton.getHash());}
            }

            int verifiedSignatures = 0;
            String hash = blockSkeleton.getHash();

            if(blockSkeleton.getSignatures().size() < 1){
                if(DEBUG_LEVEL == 1) { System.out.println("Node " + myAddress.getPort() + ": No signatures. blockskeletonID: " + blockSkeleton.getBlockId() + ". CurrentBlockID: " + currentBlock.getBlockId() 
                + " quorum: " + quorum ); }
            }

            for(BlockSignature blockSignature : blockSkeleton.getSignatures()){
                Address address = blockSignature.getAddress();
                if(containsAddress(quorum, address)){
                    if(verifySignatureFromRegistry(hash, blockSignature.getSignature(), address)){
                        verifiedSignatures++;
                    }else{
                        if(DEBUG_LEVEL == 1) { System.out.println("Node " + myAddress.getPort() + ": Failed to validate signature. blockskeletonID: " + blockSkeleton.getBlockId() + ". CurrentBlockID: " + currentBlock.getBlockId()); };
                    }
                }else{
                    if(DEBUG_LEVEL == 1) { System.out.println("Node " + myAddress.getPort() + ": blockskeletonID: " + blockSkeleton.getBlockId() + ". CurrentBlockID: " + currentBlock.getBlockId()
                    + " quorum: " + quorum + ". Address: " + address);}
                }
            }

            if(verifiedSignatures != quorum.size() - 1){
                if(DEBUG_LEVEL == 1) { System.out.println("Node " + myAddress.getPort() + ": sigs not verified for block " + blockSkeleton.getBlockId() + 
                ". Verified sigs: " + verifiedSignatures + ". Needed: " + quorum.size() + " - 1."); }
                return;
            }

            Block newBlock = constructBlockWithSkeleton(blockSkeleton);
            addBlock(newBlock);
            sendSkeleton(blockSkeleton);

        }
    }

    public Block constructBlockWithSkeleton(BlockSkeleton skeleton){
        synchronized (memPoolLock){
            if(DEBUG_LEVEL == 1) {
                System.out.println("Node " + myAddress.getPort() + ": constructBlockWithSkeleton(local) invoked");
            }
            ArrayList<String> keys = skeleton.getKeys();
            HashMap<String, Transaction> blockTransactions = new HashMap<>();
            for(String key : keys){
                if(mempool.containsKey(key)){
                    blockTransactions.put(key, mempool.get(key));
                    mempool.remove(key);
                }else{
                    // need to ask for trans
                }
            }

            Block newBlock;

                if(USE.equals("Prescription")){
                ArrayList<ArrayList<ValidationResultSignature>> listOfVRS = skeleton.getValidationResultSignatures();
                HashMap<String, ArrayList<ValidationResultSignature>> answerSigs = new HashMap<>();

                System.out.println("Node " +myAddress.getPort() + ": Trying to reconstruct block with skeleton");


                if(listOfVRS.size() > 0){
                    for(ArrayList<ValidationResultSignature> vrsList : listOfVRS){
                        PtTransaction ptTransaction = (PtTransaction) blockTransactions.get(vrsList.get(0).getVr().getPtTransactionHash()); // Assuming there is at least one VR
                        ptTransaction.setValidationResultSignatures(vrsList);
                        answerSigs.put(vrsList.get(0).getVr().getPtTransactionHash(), vrsList);
                    }
                }

                try {
                    newBlock = new PtBlock(blockTransactions,
                            getBlockHash(blockchain.getLast(), 0),
                            blockchain.size(), answerSigs);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            } else {
                newBlock = null;
            }
            
            return newBlock;
        }
    }

    private Object stateLock = new Object();
    private void stateChangeRequest(int statetoChange){
        synchronized(stateLock){
            state = statetoChange;
        }
    }

    /**
     * Adds a block
     * @param block Block to add
     */
    public void addBlock(Block block){
        stateChangeRequest(0);
        // state = 0;
        
        HashMap<String, Transaction> txMap = block.getTxList();
        HashSet<String> keys = new HashSet<>(txMap.keySet());
        ArrayList<Transaction> txList = new ArrayList<>();
        for(String hash : txMap.keySet()){
            txList.add(txMap.get(hash));
        }

        MerkleTree mt = new MerkleTree(txList);
        if(mt.getRootNode() != null) block.setMerkleRootHash(mt.getRootNode().getHash());

        blockchain.add(block);
        System.out.println("Node " + myAddress.getPort() + ": " + chainString(blockchain) + " MP: " + mempool.values());


            ArrayList<PtTransaction> ptTransactions = new ArrayList<>();

            for(String key : keys){ // For each tx key
                PtTransaction transactionInList = (PtTransaction) txMap.get(key); // cast to our PT tx
                ptTransactions.add(transactionInList);
            }

            
            if(nodeType.name().equals("Patient")){
                System.out.println("Patient looking in block for algo");
                for(String key : keys){ // For each tx key
                    PtTransaction transactionInList = (PtTransaction) txMap.get(key); // cast to our PT tx
                    if(transactionInList.getEvent().getAction().name().equals("Algorithm")){ // if its an algo
                        algorithms.add((Algorithm)transactionInList.getEvent()); // add to list
                        System.out.println("Node " + myAddress.getPort() + "(Patient): added algorithm");
                        if(algorithms.size() >= 3){ // if we have 3 or more
                            Random random = new Random();
                            algorithmSeed = algorithms.get(random.nextInt(3)).getAlgorithmSeed(); // pick 1
                            System.out.println("Node " + myAddress.getPort() + "(Patient): selected algorithm " + algorithmSeed);
                        }
                    }
                }
            }

            for(Address address : ptClients){
                System.out.println("Node " + myAddress.getPort() + ": submitted txList to client");
                Messager.sendOneWayMessage(address, new Message(Request.ALERT_WALLET, ptTransactions), myAddress);
            }
        


        /* need to add elif for use prescription. */

        this.quorum = deriveQuorum(blockchain.getLast(), 0);

        if(DEBUG_LEVEL == 1) {
            System.out.println("Node " + myAddress.getPort() + ": Added block " + block.getBlockId() + ". Next quorum: " + quorum);
        }

        if(inQuorum()){
            /* We are a Doctor */
            if(block.getBlockId() == 0){ // genesis block
                try {
                    Thread.sleep(10000); // WE go sleepy so other nodes can instantiate their BC
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                gossipTransaction(new PtTransaction(new Algorithm(algorithmSeed), String.valueOf(System.currentTimeMillis())));
                System.out.println("Node " + myAddress.getPort() + "(Doctor): submitted algorithm");
            }


            while(mempool.size() < MINIMUM_TRANSACTIONS){
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            sendQuorumReady();
        }
    }

    public void sendOneWayMessageQuorum(Message message){
        for(Address quorumAddress : quorum){
            if(!myAddress.equals(quorumAddress)) {
                Messager.sendOneWayMessage(quorumAddress, message, myAddress);
            }
        }
    }

    public boolean inQuorum(){
        synchronized (quorumLock){
            for(Address quorumAddress : quorum){
                if(myAddress.equals(quorumAddress)) {
                    return true;
                }
            }
            return false;
        }
    }

    public boolean inQuorum(Block block){
        synchronized (quorumLock){
            if(block.getBlockId() - 1 != blockchain.getLast().getBlockId()){ // 
                return false;
            }
     
            for(Address quorumAddress : quorum){
                if(myAddress.equals(quorumAddress)) {
                    return true;
                }
            }
            return false;
        }
    }

    public ArrayList<Address> deriveQuorum(Block block, int nonce){
        String blockHash;
        if(block != null && block.getPrevBlockHash() != null){
            try {
                ArrayList<Address> quorum = new ArrayList<>(); // New list for returning a quorum, list of addr
                blockHash = Hashing.getBlockHash(block, nonce); // gets the hash of the block
                BigInteger bigInt = new BigInteger(blockHash, 16); // Converts the hex hash in to a big Int
                bigInt = bigInt.mod(BigInteger.valueOf(NUM_NODES)); // we mod the big int I guess
                int seed = bigInt.intValue(); // This makes our seed
                Random random = new Random(seed); // Makes our random in theory the same across all healthy nodes
                int quorumNodeIndex; // The index from our global peers from which we select nodes to participate in next quorum
                Address quorumNode; // The address of thenode from the quorumNode Index to go in to the quorum
                //System.out.println("Node " + myAddress.getPort() + ": blockhash" + chainString(blockchain));
                while(quorum.size() < QUORUM_SIZE){
                    quorumNodeIndex = random.nextInt(globalPeers.get(0).size()); // may be wrong but should still work
                    quorumNode = globalPeers.get(0).get(quorumNodeIndex);
                    if(!containsAddress(quorum, quorumNode)){
                        //System.out.println("Added doctor to q");
                        quorum.add(globalPeers.get(0).get(quorumNodeIndex));
                    }
                }
                return quorum;
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private HashMap<String, Address> accountsToAlert;

    public void alertWallet(String accountPubKey, Address address){
        synchronized(accountsLock){
        ptClients.add(address);
        }
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
                    System.out.println(e);
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
    // class HeartBeatMonitor extends Thread {
    //     Node node;
    //     HeartBeatMonitor(Node node){
    //         this.node = node;
    //     }

    //     public void run() {
    //         try {
    //             Thread.sleep(10000);
    //         } catch (InterruptedException e) {
    //             e.printStackTrace();
    //         }
    //         while (true) {
    //             for(Address address : localPeers){
    //                 try {                 
    //                     Thread.sleep(10000);
    //                     Message messageReceived = Messager.sendTwoWayMessage(address, new Message(Message.Request.PING), myAddress);

    //                     /* Use heartbeat to also output the block chain of the node */

    //                 } catch (InterruptedException e) {
    //                     System.out.println("Received Interrupted Exception from node " + address.getPort());
    //                     throw new RuntimeException(e);
    //                 } catch (ConcurrentModificationException e){
    //                     System.out.println(e);
    //                     break;
    //                 } catch (IndexOutOfBoundsException e){
    //                     System.out.println(e);
    //                 }
    //             }
    //         }
    //     }
    // }

    private final int MAX_PEERS, NUM_NODES, QUORUM_SIZE, MIN_CONNECTIONS, DEBUG_LEVEL, MINIMUM_TRANSACTIONS;
    private final Object lock, quorumLock, memPoolLock, quorumReadyVotesLock, memPoolRoundsLock, sigRoundsLock, blockLock, accountsLock;
    private int quorumReadyVotes, memPoolRounds, algorithmSeed;
    private ArrayList<Address> localPeers, ptClients;
    private ArrayList<Algorithm> algorithms;
    private HashMap<String, Transaction> mempool;
    HashMap<String, Integer> accounts;
    private ArrayList<BlockSignature> quorumSigs;
    private LinkedList<Block> blockchain;
    private final Address myAddress;
    private ServerSocket ss;
    private Block quorumBlock;
    private PrivateKey privateKey;
    private int state;
    public NodeType nodeType;
    public final String USE;
    private ArrayList<Address> quorum;
    private ArrayList<ArrayList<Address>> globalPeers;
    public static double Q_TIME;
    public Integer[] allAlgorithms = {1, 2, 3, 4, 5, 6, 7};
    public static int shardNumber;
    public static double quorumConsensusTime;
}
