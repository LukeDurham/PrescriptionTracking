import node.Node;
import node.NodeType;
import node.communication.Address;
import node.communication.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * Launches a network given specified configurations
 */
public class NetworkLauncher {

    NodeType nt;

    /* Make a list of the entirety of each node's address */
    private static final ArrayList<ArrayList<Address>> globalPeers = new ArrayList<ArrayList<Address>>();

    public static void main(String[] args) {
        String usage = "Usage: NetworkLauncher " +
                "[-o <see options>] [-t <TimedWaitDelayMilliseconds>]" +
                "\n Options:" +
                "\n -o <myNodesStartingPort> <myNodesEndingPort> <otherSubNetStartingPort> <otherSubNetEndingPort> <otherSubNetHostName> ..."
                +
                "\n     Specifies information regarding other subnets of nodes. " +
                "\n     First we specify our range of port for localhost, then list other subnets." +
                "\n     Total number of nodes must be under the specified amount in config.properties" +
                "\n     No limit for number of subnets one can reasonably specify" +
                "\n\n -t <TimedWaitDelayMilliseconds>" +
                "\n     Specifies the time for a subnet to wait before seeking out connections. " +
                "\n     Useful to allow all subnets to bind to their ports before connecting" +
                "\n\n Default: NetworkLauncher will launch number of nodes specified in config.properties " +
                "\n on localhost with no other scope of nodes";
        try {
            /* Grab values from config file */
            String configFilePath = "src/main/java/config.properties";
            FileInputStream fileInputStream = new FileInputStream(configFilePath);
            Properties prop = new Properties();
            prop.load(fileInputStream);

            int numNodes = Integer.parseInt(prop.getProperty("NUM_NODES"));
            int maxConnections = Integer.parseInt(prop.getProperty("MAX_CONNECTIONS"));
            int minConnections = Integer.parseInt(prop.getProperty("MIN_CONNECTIONS"));
            int startingPort = Integer.parseInt(prop.getProperty("STARTING_PORT"));
            int quorumSize = Integer.parseInt(prop.getProperty("QUORUM"));
            int minimumTransactions = Integer.parseInt(prop.getProperty("MINIMUM_TRANSACTIONS"));
            int debugLevel = Integer.parseInt(prop.getProperty("DEBUG_LEVEL"));
            String use = prop.getProperty("USE");

            /* List of node objects for the launcher to start */
            ArrayList<Node> nodes = new ArrayList<Node>();

            int timedWaitDelay = 0;

            if (args.length > 0 && args[0].equals("-t")) {
                timedWaitDelay = Integer.parseInt(args[1]);
            }

            for (int i = startingPort; i < (startingPort) + (numNodes / 2); i++) {
                nodes.add(new Node(NodeType.Doctor, use, i, maxConnections, minConnections, numNodes, quorumSize,
                        minimumTransactions, debugLevel));
                System.out.println("Added Doctor");
            }

            for (int i = (startingPort) + (numNodes / 2); i < startingPort + numNodes; i++) {
                nodes.add(new Node(NodeType.Patient, use, i, maxConnections, minConnections, numNodes, quorumSize,
                        minimumTransactions, debugLevel));
                System.out.println("Added Patient");
            }

            try {
                Thread.sleep(timedWaitDelay);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // StringTokenizer st;
            // String path = "./src/main/java/node/nodeRegistry/";
            // File folder = new File(path);
            // File[] registeredNodes = folder.listFiles();

            // for (int i = 0; i < registeredNodes.length; i++) {
            // String name = registeredNodes[i].getName();

            // if(!name.contains("keep")){
            // st = new StringTokenizer(name, "_");
            // String host = st.nextToken();
            // int port = Integer.parseInt(st.nextToken().replaceFirst(".txt", ""));
            // globalPeers.add(new Address(port, host, NodeType.Doctor));
            // }
            // }

            // initialize globalPeers
            ArrayList<Address> doctorList = new ArrayList<>();
            ArrayList<Address> pharmacistList = new ArrayList<>();
            ArrayList<Address> patientList = new ArrayList<>();
            globalPeers.add(doctorList);
            globalPeers.add(pharmacistList);
            globalPeers.add(patientList);

            /* DOES NOT WORK FOR R2 */
            // comeback AARON
            for (Node node : nodes) {
                // patients first because we have the most patients and don't have to go through
                // the worst case if statements
                if (node.nodeType.name().equals("Patients")) {
                    globalPeers.get(2).add(node.getAddress());
                } else if (node.nodeType.name().equals("Doctors")) {
                    globalPeers.get(0).add(node.getAddress());
                } else {
                    globalPeers.get(1).add(node.getAddress());
                }
                System.out.println(node.getAddress().getNodeType().name());
            }

            NetworkLauncher n = new NetworkLauncher();
            n.startNetworkClients(globalPeers, nodes); // Begins network connections

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Error: args formatted incorrect" + e);
            System.out.println(usage);
        }
    }

    /* Gives each node a thread to start node connections */
    public void startNetworkClients(ArrayList<ArrayList<Address>> globalPeers, ArrayList<Node> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            // Collections.shuffle(globalPeers);
            new NodeLauncher(nodes.get(i), globalPeers).start();
        }
    }

    /**
     * Thread which is assigned to start a single node within the NetworkLaunchers
     * managed nodes
     */
    class NodeLauncher extends Thread {
        Node node;
        ArrayList<ArrayList<Address>> globalPeers;

        NodeLauncher(Node node, ArrayList<ArrayList<Address>> globalPeers) {
            this.node = node;
            this.globalPeers = globalPeers;
        }

        public void run() {
            node.requestConnections(globalPeers);
        }
    }
}
