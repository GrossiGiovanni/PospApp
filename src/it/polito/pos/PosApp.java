package it.polito.pos;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

public class PosApp{
    private Status status;
    private Merchant merchant;
    private Map<String, Issuer> issuers = new HashMap<>();
    private Map<String, String> iinMap = new HashMap<>();
    private LocalDate currentDate;
    private Payment currentPayment;
    private Map<String, List<String>> transactionsByIssuer = new HashMap<>();
    private Map<String, Double> totalsByIssuer = new HashMap<>();
    private Payment lastSuccesfulPayment;
    /**
     * Define the current status of the POS
     */
    public enum Status {
        /** Initial state */
        IDLE, 
        /** State after amount set */
        STARTED, 
        /** State after card swiped */
        READ, 
        /** State after attempted paymend with wrong PIN */
        WRONG_PIN, 
        /** State after attempted paymend with error */
        DECLINED, 
        /** State after paymend successful */
        SUCCESS }

    // R1 - configuration

    /**
     * Register the merchant information as
     * three text lines having max 20 characters length.
     * The lines are then printed on the receipt.
     * 
     * @param line1 first line
     * @param line2 second line
     * @param line3 third line
     * @throws PosException if lines are null or longer that 20 chars
     */
    public void setMerchantInfo(String line1, String line2, String line3) throws PosException {
        if (this.merchant == null) {
            this.merchant = new Merchant(line1,line2, line3);
        } else {
            this.merchant.setInfo(line1, line2, line3);
        }
    }
        
    

    /**
     * Retrieves the lines forming the merchant info
     * 
     * @return the merchant info
     */
  
     public String getMerchantInfo() {
        return this.merchant.getInfo();
    }


    /**
     * Register a server proxy for a given issuer providing
     * a server that is going to support transactions for that issuer
     * and a list of Issuer Identification Number (IIN)
     * 
     * @param name  the name of the card issuer (e.g. VISA)
     * @param server the server object
     * @param iins   the Issuer Identification Numbers
     */
    public void registerIssuer(String name, Issuer server, String... iins) {
        issuers.put(name, server);
        for (String iin : iins) {
            iinMap.put(iin, name);
        }
    }
    /**
     * returns the name of the matching card issuer if any.
     * 
     * A card number matches an IIN id the card number starts
     * with the same digits as IIN
     * 
     * @param cardNumber the credit card number
     * @return the name of the card issuer
     * @throws PosException if no issuer IIN match the card number
     */
    public String getIssuer(String cardNumber) throws PosException {
        for (String iin : iinMap.keySet()) {
            if (cardNumber.startsWith(iin)) {
                return iinMap.get(iin);
            }
        }
        throw new PosException("No issuer IIN match the card number");   }

    /**
     * Retrived the current date for the POS system
     * By defaul is the acutal system date. i.e. LocalDate.now()
     * 
     * @return current date
     */
    public LocalDate currentDate() {
        return this.currentDate;
    }

    /**
     * Sets the new current date for the system.
     * 
     * @param today the new current date
     */
    public void setCurrentDate(LocalDate today){
        this.currentDate = today != null ? today :LocalDate.now();
    }

    // R2 - Basic transaction

    /**
     * Retrieves the current status of the POS
     * 
     * @return current status
     */
    public PosApp() {
        this.status = Status.IDLE;  
        this.currentDate=LocalDate.now();
    }

    public Status getStatus(){
        return status;
    }

    /**
     * Start a payment transaction by defining the amount to be payed
     * The current status must be IDLE
     * Transitions the status into STARTED.
     * 
     * @param amount   payment amount
     * @throws PosException in case the current status is not IDLE
     */
    public void beginPayment(double amount) throws PosException {
        if ( status != Status.IDLE) {
            throw new PosException("Cannot begin payment,not in IDLE state");
        }
        currentPayment = new Payment(amount);
        status = Status.STARTED;
    }

    /**
     * Accepts the data read when the card is swiped through the magnetic stripe reader
     * The current status must be STARTED or DECLINED
     * Transitions the status into READ otherwise it becomes DECLINED
     * 
     * @param cardNumber    card number
     * @param client        client name
     * @param expiration    expiration
     * @throws PosException if the current state is not STARTED or the card data is not correct or card is expired
     */
    public void  readStripe(String cardNumber, String client, String expiration)throws PosException {
        if (currentPayment == null|| currentPayment.getStatus() !=Status.STARTED && currentPayment.getStatus()!=Status.DECLINED) {
            throw new PosException("Cannot read  stripe,POS is not in the correct state.");
        }
        String issuerName;
        try{
            issuerName = getIssuer(cardNumber);
        } catch(PosException e){
            currentPayment.setStatus(PosApp.Status.DECLINED);
            this.status=PosApp.Status.DECLINED;
            throw new PosException("card from unkown issuer");
        }
        if(!issuers.containsKey(issuerName)){
            currentPayment.setStatus(PosApp.Status.DECLINED);
            this.status=PosApp.Status.DECLINED;
            throw new PosException("card from unkown issuer");
        }
        LocalDate expDate;
        try{
             int year=Integer.parseInt(expiration.substring(2, 4) ) + 2000;
             int month = Integer.parseInt(expiration.substring(0, 2) );
             expDate=LocalDate.of(year,month,1);
            }
           catch(NumberFormatException | DateTimeException e){
            currentPayment.setStatus(PosApp.Status.DECLINED);
            this.status=PosApp.Status.DECLINED;
            throw new PosException("Invalid expiration date");
           }
        LocalDate today = currentDate != null ? currentDate : LocalDate.now();
        if (expDate.isBefore(today)) {
            currentPayment.setStatus(PosApp.Status.DECLINED);
            this.status=PosApp.Status.DECLINED;
            throw new PosException("Card expired.");
        }
        if(!isLuhnValid(cardNumber)){
            currentPayment.setStatus(PosApp.Status.DECLINED);
            this.status=PosApp.Status.DECLINED;
            throw new PosException("Credit card number failde Luhn validation.") ; 
        }

        currentPayment.setCardDetails(cardNumber, client, expDate);
        currentPayment.setStatus(PosApp.Status.READ);
        this.status = PosApp.Status.READ;
    }
    

    /**
     * Performs the payment with the PIN that has been entered by the user
     * 
     * It contacts the server associated with the card issuer and in case of
     * success returns the transaction ID provided by the issuer server.
     * 
     * The current status must be READ or WRONG_PIN
     * Transitions the status into SUCCESS if successful, 
     * WRONG_PIN if there was a PIN error or DENIED in case of transaction
     * denied by the server.
     * 
     * After three consecutives PIN errors the status is DECLINED
     * 
     * @param pin   the PIN entered by the user
     * @return the transaction ID 
     * @throws PosException in case of error
     */
    public String performPayment(String pin) throws PosException {
        if(currentPayment==null ||(currentPayment.getStatus() != Status.READ && currentPayment.getStatus() != Status.WRONG_PIN)) {
            throw new PosException("Cannot perform payment, POS isnot in the correct stat");
        }

        String issuerName = getIssuer(currentPayment.getCardNumber());
        Issuer issuer = issuers.get(issuerName);

        if (issuer == null) {
            throw new PosException("Issuer not found for the card.");
        }

        try{
            String transactionId = currentPayment.processPayment(issuer, pin);

            this.status=currentPayment.getStatus();
            

            if (currentPayment.getStatus()==Status.SUCCESS){
                this.status=PosApp.Status.SUCCESS;
                transactionsByIssuer.computeIfAbsent(issuerName, k -> new ArrayList<>()).add(transactionId);
                totalsByIssuer.merge(issuerName, currentPayment.getAmount(), Double::sum);}

            
            return transactionId;}
            catch (PosException e) {
                this.status = currentPayment.getStatus();
                throw e;
            }
    }


    // R3 - Extended transaction

    /**
     * Makes the POS ready for a new transaction after a successful payment
     * From status SUCCESS moves into IDLE
     */
    public void reset() throws PosException {
        if (currentPayment == null || currentPayment.getStatus() != Status.SUCCESS) {
            throw new PosException("Cannot reset, no successful payment or no payment in progress.");
        }
        lastSuccesfulPayment = currentPayment;

        currentPayment = null;  
        status=Status.IDLE ; // Clear the current payment
    }

    /**
     * Cancels (rollback) a successful transaction.
     * Calls the card issuer server providing the transaction ID and the amount
     * From status SUCCESS moves into IDLE if transaction was cancelled
     * 
     * @return ID of the cancelled transaction if server confirmed
     * @throws PosException if not in SUCCESS state or in case of error from the server
     */
    public String cancelTransaction() throws PosException {
        if (currentPayment == null || currentPayment.getStatus() != Status.SUCCESS) {
            throw new PosException("Cannot cancel transaction, current state is not SUCCESS.");
        }
    
        String transactionId = currentPayment.getTransactionId();
        String issuerName = getIssuer(currentPayment.getCardNumber());
        Issuer issuer = issuers.get(issuerName);
    
        // Attempt to cancel the transaction
        TransactionResult result = issuer.cancelPurchase(transactionId, currentPayment.getAmount());
    
        if (result.getResultCode() != TransactionResult.OK) {
            throw new PosException("Cancellation  failed: " +result.getReason());
        }
    
        currentPayment = null; 
        status = Status.IDLE;
        return transactionId;
    }
    /**
     * Terminates the transaction after and error
     * From status WRONG_PIN or DECLINED
     * moved back to IDLE
     */
    public void abortPayment() throws PosException {
        if (currentPayment == null || (currentPayment.getStatus() != Status.WRONG_PIN && currentPayment.getStatus() != Status.DECLINED)) {
            throw new PosException("Can't cancel payment, current state is not WRONG_PIN or DECLINED.");
        }
    
        currentPayment = null;
        status = Status.IDLE;
    }


    // R4 - Stats

    /**
     * Prints the receipt of the latest transaction that contains:
     * 
     * 
     * <ul>
     * <li> the merchant info
     * <li> the date of the payment
     * <li> the last 4 digits of the card number
     * <li> the amount of the payment
     * <li> the result of the payment (OK or ERROR) 
     * <li> the transaction ID if OK
     * </ul>
     * on distinct lines
     * 
     * @return the string of the receipt
     */
    public String receipt() throws PosException {
        Payment paymentToUse = currentPayment != null ? currentPayment : lastSuccesfulPayment;
        if (paymentToUse ==null || paymentToUse.getStatus()!=Status.SUCCESS){
            throw new PosException("No succesful transaction available for recepit.");
        }
        String lastFourDigits = paymentToUse.getCardNumber().substring(paymentToUse.getCardNumber().length()-4);
        String result = paymentToUse.getStatus()==Status.SUCCESS? "OK" : "ERROR";
        LocalDate paymentDate = currentDate != null ? currentDate : LocalDate.now();

        return "merchant:" + merchant.getInfo()+"\n " +
                "Date " + paymentDate +"\n"+
                "Transaction Id  :" + paymentToUse.getTransactionId() + "\n" + 
                "Card : **** **** ****" +  lastFourDigits + "\n" +
                "Amount : " + paymentToUse.getAmount() +  "\n"+
                "Result : " + result +"\n" + 
                "Transaction ID" + paymentToUse.getTransactionId(); 
    }

    /**
     * Returns a map having the issuers as keys and the lists
     * of completed transaction IDs as values
     * 
     * @return the map issuer - transaction list
     */
    public Map<String,List<String>> transactionsByIssuer() throws PosException{
        if (transactionsByIssuer.isEmpty()){
            throw new PosException("no transactions available");
        }
        return transactionsByIssuer;
    }

    /**
     * Returns a map having the issuers as keys and the total
     * amount of successful transactions as values
     * 
     * @return the map issuer - transaction list
     */
    public Map<String,Double> totalByIssuer() throws PosException{
        if (totalsByIssuer.isEmpty()){
         throw new PosException("No totals available");
        }
        return totalsByIssuer;
    }

    private boolean isLuhnValid(String cardNumber) {
        String payload = cardNumber.substring(0, cardNumber.length() - 1);  // Rimuove l'ultimo numero (parity digit)
        int parityDigit = Character.getNumericValue(cardNumber.charAt(cardNumber.length() - 1));  // Parity digit
    
        int sum = 0;
        boolean doubleDigit = true;
    
        // Scorre il payload da destra a sinistra
        for (int i = payload.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(payload.charAt(i));
    
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit = digit - 9;  // Se il doppio del numero è maggiore di 9, somma le cifre
                }
            }
    
            sum += digit;
            doubleDigit = !doubleDigit;
        }
    
        // Calcola il numero di parità
        int calculatedParity = (10 - (sum % 10)) % 10;
    
        // Confronta con il numero di parità originale
        return calculatedParity == parityDigit;
    }
}
