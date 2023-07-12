package node.blockchain.prescription;

import java.util.ArrayList;

import node.blockchain.Transaction;
import node.blockchain.prescription.Events.Algorithm;
import node.blockchain.prescription.Events.Prescription;
import node.communication.ValidationResultSignature;
import node.communication.utils.Hashing;

public class PtTransaction extends Transaction {

    private Event event;

    private ArrayList<ValidationResultSignature> validationResultSignatures;

    public ArrayList<ValidationResultSignature> getValidationResultSignatures() {
        return validationResultSignatures;
    }

    public void setValidationResultSignatures(ArrayList<ValidationResultSignature> validationResultSignatures) {
        this.validationResultSignatures = validationResultSignatures;
    }

    public PtTransaction(Event event, String timestamp){
        this.event = event;
        this.timestamp = timestamp;
        UID = Hashing.getSHAString(toString() + timestamp);
    }

    public Event getEvent() {
        return event;
    }

    @Override
    public String toString() {
        if(event.getAction().name().equals("Prescription")){
            Prescription prescription = (Prescription) event;
            return event.getPatientUID() + ", " + event.getAction().name() + ", ";
        }else if (event.getAction().name().equals("Algorithm")){
            Algorithm algorithm = (Algorithm) event;
            return event.getAction().name() + ", " + algorithm.getAlgorithmSeed();
        }else{
            return null;
        }
    }    
}