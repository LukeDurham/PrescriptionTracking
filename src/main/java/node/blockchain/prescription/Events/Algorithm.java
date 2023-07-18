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

    public boolean runAlgorithm(PtTransaction transaction){
        // simulate a heavy function
        Random random = new Random();
        try {
            Thread.sleep(5000); // Simulate delay
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int flip = random.nextInt(2);
        return flip == 0;
    }
}
