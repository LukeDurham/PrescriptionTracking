pharmacies=("Walgreens" "Walmart" "CVS")
medications=("Adderall" "Xanax" "Fentanyl" "Ibuprofen" "Aleve")
dosageAmount=(10 20 30 40 50 60)

for i in {1..100}
do
    echo "t"
    echo ${pharmacies[$((RANDOM % 3))]}
    echo ${medications[$((RANDOM % 5))]}
    echo ${dosageAmount[$((RANDOM % 5))]}
    echo ${dosageAmount[$((RANDOM % 6))]}
done > inputs.txt


    # Use the generated inputs to call your Java program
    java -cp target/network-1.0-SNAPSHOT.jar client.Client < inputs.txt
done






