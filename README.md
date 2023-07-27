# PharamSys

<p align="center">
  <img src="https://github.com/peytonlundquist/network/blob/master/bluechainlogo.png"  width="300" height="300">
</p>


## A distributed, decentralized blockchain network and research use-case implemented in Java.

## Description
This network and research use-case was developed for the purpose of student researchers, interested in developing blockchain solutions. We provide an alternate prescription-tracking blockchain network system implemented ontop of the BlueChain network.

This software is not a public network costing gas fees to expirement with, nor is its implementation complex. With this, researchers or anyone is encouraged to fork our software to use as a starting point or to implement their use case.

We provide:
  - Altered consensus mechanism with sharding
  - Easy configurability
  - A simplified code base
  - A working extended implementation on the Bluechain platform

This software uses the following concepts in order to achieve a fully distributed, decentralized blockchain network:
  - Servent (Server + Client)
  - Java Sockets + TCP/IP
  - Multi-threading
  - Distributed Systems
  - Decentralization
  - Quorum Consensus
  - Sharding
  
Pharmasys is not production-grade software, and should not be externally hosted unless proper security has been implemented first.
  - Lack of certain securities
  - Few fail-safe mechanisms
  - Brand-New
  - Short-Lived Network
  - Documentation unclear

## How to Use
### Prerequisites
  - Java 11
  - Maven

### Running a Local Network
  1. Navigate to the config.properties file (network/src/main/java/config.properties)
  2. Configure the network to the specifications you desire. 
  
  - **Warning:** The number of nodes your local machine can handle depends on the computing resources that the machine has.
  
  3. Use the startNetwork shell script (navigate back to network/)
    
    ./startNetwork.sh
    
  4. Launch the client associated with the use case defined in the config file
   - For the Defi use case for example, run 
    
    ./startPrescriptionClient.sh 
    Enter "t" in the client to submit transactions
      
