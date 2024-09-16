package it.polito.pos;

public class Merchant {
    private String line1; 
    private String line2;
    private String line3; 


    
    public Merchant(String line1, String line2, String line3) throws PosException {
        setInfo(line1, line2, line3);
    }
    
    public void setInfo(String line1, String line2, String line3) throws PosException {
        if (line1 == null || line2 == null || line3 == null || 
            line1.length() > 20 || line2.length() > 20 || line3.length() > 20) {
            throw new PosException("Merchant info lines must be non-null and shorter than 20 characters.");
        }
        this.line1 = line1;
        this.line2 = line2;
        this.line3 = line3;
    }

    public String getInfo() {
        return String.join("\n", line1, line2, line3);
    }
}

