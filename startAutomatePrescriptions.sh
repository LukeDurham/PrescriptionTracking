

# Array of pharmacies
pharmacies=('Walgreens' 'Walmart' 'CVS')

# Array of medications
medications=('Adderall' 'Xanax' 'Fentanyl' 'Ibuprofen' 'Aleve')

# Array of dosages
dosages=(10 20 30 40 50 60)

# Run the script 100 times
for ((i = 1; i <= 100; i++)); do
    # Select random values
    pharmacy=${pharmacies[$RANDOM % ${#pharmacies[@]}]}
    medication=${medications[$RANDOM % ${#medications[@]}]}
    dosage=${dosages[$RANDOM % ${#dosages[@]}]}

    # Use the generated inputs to call your Java program
    java -cp . client.Client <<< $'t\n'"$pharmacy"'\n'"$medication"'\n'"$dosage mg"'\n'"$dosage"
done






