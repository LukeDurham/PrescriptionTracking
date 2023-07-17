#!/bin/bash

# Create an array for pharmacies, medication names, and dosages
pharmacies=("Walgreens" "Walmart" "CVS")
medicationNames=("Adderall" "Xanax" "Fentanyl" "Ibuprofen" "Aleve")
dosages=(10 20 30 40 50 60)

# Loop to send input 100 times
for (( i=0; i<100; i++ ))
do
  # Select a random pharmacy, medication, and dosage
  pharmacy=${pharmacies[$RANDOM % ${#pharmacies[@]}]}
  medication=${medicationNames[$RANDOM % ${#medicationNames[@]}]}
  dosage=${dosages[$RANDOM % ${#dosages[@]}]}
  
  # Send the 't' command followed by the random selections
  echo "t"
  echo "$pharmacy"
  echo "$medication"
  echo "$dosage"
done | java -classpath . client.Client  # Replace with the correct path to your compiled Java program
