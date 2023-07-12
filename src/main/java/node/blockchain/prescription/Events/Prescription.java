package node.blockchain.prescription.Events;

import java.util.Date;

import node.blockchain.prescription.Event;

public class Prescription extends Event {

    private Date date;
    private int amount;
    private String medication;
    private String doctorName;
    private String dosage;
    
    public String getDosage() {
        return dosage;
    }

    public int getAmount() {
        return amount;
    }

    public Date getDate() {
        return date;
    }

    public String getMedication() {
        return medication;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public Prescription(String patientUID, String pharmacyName, String doctorName, String medication, String dosage, Date date, int amount) {
        super(patientUID, Action.Prescription);
        this.doctorName = doctorName;
        this.date = date;
        this.amount = amount;
        this.medication = medication;
        this.dosage = dosage;
    }
    
}
