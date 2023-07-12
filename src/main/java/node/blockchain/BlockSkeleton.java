package node.blockchain;

import node.communication.BlockSignature;
import node.communication.ValidationResultSignature;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Set;

public class BlockSkeleton implements Serializable{
    private final int blockId;
    private String hash;
    private final ArrayList<String> keys;
    private ArrayList<BlockSignature> signatures;
    private ArrayList<ArrayList<ValidationResultSignature>> validationResultSignatures;


    public ArrayList<ArrayList<ValidationResultSignature>> getValidationResultSignatures() {
        return validationResultSignatures;
    }

    public BlockSkeleton (int blockId, ArrayList<String> keys, ArrayList<BlockSignature> signatures, String hash, ArrayList<ArrayList<ValidationResultSignature>> validationResultSignatures){
        this.keys = keys;
        this.blockId = blockId;
        this.signatures = signatures;
        this.hash = hash;
        this.validationResultSignatures = validationResultSignatures;
    }

    public ArrayList<BlockSignature> getSignatures() {return signatures;}
    public int getBlockId(){return blockId;}
    public ArrayList<String> getKeys() {return keys;}
    public String getHash(){return hash;}
}

