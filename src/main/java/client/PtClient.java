package client;
import java.util.*;
import java.io.*;
import java.net.Socket;
import java.security.KeyPair;
import java.security.PrivateKey;

import node.communication.Address;
import node.communication.ValidationResultSignature;
import node.communication.messaging.Message;
import node.communication.messaging.Messager;
import node.communication.utils.DSA;
import node.blockchain.defi.Account;
import node.blockchain.defi.DefiTransaction;
import node.blockchain.merkletree.MerkleTreeProof;


// How would we interpret account in our use case? Make sure peyton addresses this. import node.blockchain.defi.Account; 
import node.blockchain.prescription.PtTransaction;
import node.blockchain.prescription.Events.Prescription;


public class PtClient {

    Object updateLock;
    BufferedReader reader;
    Address myAddress;
    ArrayList<Address> fullNodes; //list of Doctors addresses in the quorum 
    String doctorName;
    String fileName = "transactionperminute.csv";


    public PtClient(Object updateLock, BufferedReader reader, Address myAddress, ArrayList<Address> fullNodes) {
        this.updateLock = updateLock;
        this.reader = reader;
        this.myAddress = myAddress;
        this.fullNodes = fullNodes;
        doctorName = "John Doe";
    }

    protected void submitPrescription() throws IOException {
        alertFullNode();
        // int transactionCounter = 0;
        
        System.out.println("Generating Transaction");
        System.out.println("Enter the Pharamacy"); //improve on this concept.
        // String pharmacy = reader.readLine();
        String pharmacy = "CVS";
        System.out.println("Enter the medication name"); 
        // String medication = reader.readLine();
        String medication = "Adderall";
        System.out.println("What is the dosage"); 
        // String dosage = reader.readLine();
        String dosage = "20mg";
        System.out.println("How many?");
        // int amount = Integer.parseInt(reader.readLine());
        int amount = 30; ///dosage

        Date date = new Date();

        submitTransaction(new PtTransaction(
        new Prescription("TestPatient", pharmacy, doctorName, medication, dosage, new Date(date.getTime()), 
        amount), String.valueOf(System.currentTimeMillis())), fullNodes.get(0));
        long startTime = System.nanoTime(); // start the timer
        double seconds = startTime / 1_000_000_000.0;

        try {
            FileWriter writer = new FileWriter(fileName);
            // Write column names
            writer.append("PatientUID ");
            writer.append(',');
            writer.append("Start Time ");
            writer.append(',');
            writer.append("End Time ");
            writer.append(',');
            writer.append("\n");
            writer.append("Test Patient ");
            writer.append(',');
            writer.append(String.valueOf(seconds) + " ");
            writer.append(',');

            writer.flush();
            writer.close();

            System.out.println("CSV file with headers created successfully...");

        } catch (IOException e) {
            System.out.println("Error occurred while creating CSV...");
            e.printStackTrace();

        }


        // transactionCounter++;

        System.out.println("PTClient submitted prescription");

    }

    protected void submitTransaction(PtTransaction transaction, Address address){
        try {
            Socket s = new Socket(address.getHost(), address.getPort());
            OutputStream out = s.getOutputStream();
            ObjectOutputStream oout = new ObjectOutputStream(out);
            Message message = new Message(Message.Request.ADD_TRANSACTION, transaction);
            oout.writeObject(message);
            oout.flush();
            Thread.sleep(1000);
            s.close();
        } catch (IOException e) {
            System.out.println("Full node at " + address + " appears down.");
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    protected void alertFullNode() throws IOException{
        synchronized(updateLock){
            Messager.sendOneWayMessage(new Address(fullNodes.get(0).getPort(), fullNodes.get(0).getHost(), null), 
            new Message(Message.Request.ALERT_WALLET, myAddress), myAddress);

            System.out.println("PTClient alerted full node");
        }
    }


    protected void readIncomingTransactions(ArrayList<PtTransaction> ptTransactions){
        long endTime = System.nanoTime(); // end the timer
        double seconds = endTime / 1_000_000_000.0;
    
        try {
            // Open the FileWriter in append mode (true)
            FileWriter writer = new FileWriter(fileName, true);
    
            // Write the elapsed time in seconds to a new line
            writer.append(String.valueOf(seconds)).append("\n");
    
            writer.flush();
            writer.close();
    
        } catch (IOException e) {
            System.out.println("Error occurred while updating CSV...");
            e.printStackTrace();
        }
    
        for(PtTransaction ptTransaction : ptTransactions){
            int trueCounter = 0;
            int falseCounter = 0;
    
            for(ValidationResultSignature vrs : ptTransaction.getValidationResultSignatures()){
                if(vrs.getVr().isValid()){
                    trueCounter++;
                }else{
                    falseCounter++;
                }
            }
    
            if(trueCounter > falseCounter){
                System.out.println("TX " + ptTransaction.getUID() + " Valid. Yes votes: " + trueCounter + ". No votes: " + falseCounter);
            }else{
                System.out.println("TX " + ptTransaction.getUID() + " Invalid. Yes votes: " + trueCounter + ". No votes: " + falseCounter);
            }
        }
    }
    
}
