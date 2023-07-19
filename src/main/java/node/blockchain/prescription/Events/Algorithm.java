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
    //O(1) method
    public boolean getGPA(PtTransaction transaction)
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
    public boolean getRating(PtTransaction transaction)
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
    public boolean getSchool(PtTransaction transaction)
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
    public boolean getField(PtTransaction transaction)
    {
        Random random = new Random();
        int flip = random.nextInt(2);
        if(flip == 0){
            return true;
        }else{
            return false;
        }
    }


    //O(n) method
    public boolean getHistory(/* some data structure of all doctor prescriptions */)
    {
        Random random = new Random();
        int flip = random.nextInt(2);
        if(flip == 0){
            return true;
        }else{
            return false;
        }
    }
    
    //O(n)
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

    

    public boolean runAlgorithm(PtTransaction transaction , int shard){
        //Random random = new Random(algorithmSeed);
        switch (shard) {
        case 1:
            System.out.println("Working on getAge function");
            return getAge(transaction);
        
        case 2: 
        {
            System.out.println("Working on getExperience function");
            return getExperience(transaction);
        }
        case 3:
        {
            System.out.println("Working on getGPA function");
            return getGPA(transaction);
        }
        case 4:
        {
            System.out.println("Working on getSchool function");
            return getSchool(transaction);
        }
        case 5:
        {
            System.out.println("Working on getField function");
            return getField(transaction);
        }
        case 6:
        {
            System.out.println("Working on getHistory function");
            return getHistory();
        }
        case 7:
        {
            System.out.println("Working on getNumberOfPrescriptions function");
            return getNumberOfPrescriptions();
        }

        default:
        System.out.println("RUNNING DEFAULT... should not happen.");
        Random random2 = new Random();
        int flip = random2.nextInt(2);
        if(flip == 0){
            return true;
        }else{
            return false;
        }
    }
}
}