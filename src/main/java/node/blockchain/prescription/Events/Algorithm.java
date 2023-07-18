package node.blockchain.prescription.Events;

import java.util.Random;

import node.blockchain.prescription.Event;
import node.blockchain.prescription.PtTransaction;

public class Algorithm extends Event{

    private int algorithmSeed;

    public int getAlgorithmSeed() {
        return algorithmSeed;
    }

    public Algorithm(int algorithmSeed) {
        super(Action.Algorithm);
        this.algorithmSeed = algorithmSeed;
    }

    //O(1) method
    public boolean getAge(PtTransaction transaction)
    {
        Random random = new Random();
        int flip = random.nextInt(2);
        if(flip == 0){
            return true;
        }else{
            return false;
        }
    }
    //O(1) method
    public boolean getExperience(PtTransaction transaction)
    {
        Random random = new Random();
        int flip = random.nextInt(2);
        if(flip == 0){
            return true;
        }else{
            return false;
        }
    }

    public boolean getNumberOfPrescriptions(/* some data structure of all doctor prescriptions */)
    {
        Random random = new Random();
        /*int size = dataStructure.size() */
        int randomSeed = 2 /* + size */;
        int flip = random.nextInt(randomSeed);
        if (flip % 2 == 0)
        {
            return true;
        }
        return false;
    }

    

    public boolean runAlgorithm(PtTransaction transaction /*, int shard */){
        //Random random = new Random(algorithmSeed);
        int theAlgorithm = 10;
        if (theAlgorithm == 0)
        {
            return getAge(transaction);
        }
        else if (theAlgorithm == 1)
        {
            return getExperience(transaction);
        }
        else if (theAlgorithm == 2)
        {
            return getNumberOfPrescriptions();
        }
        Random random2 = new Random();
        int flip = random2.nextInt(2);
        if(flip == 0){
            return true;
        }else{
            return false;
        }
    }
}