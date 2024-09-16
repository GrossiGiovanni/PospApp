package it.polito.pos;

import java.time.LocalDate;

public class Payment {
    private double amount;
    private String cardNumber;
    private String cardHolderName;
    private LocalDate expirationDate;
    private String transactionId;
    private PosApp.Status status;
    private int pinAttempts = 0;

    public Payment(double amount) {
        this.amount = amount;
        this.status = PosApp.Status.STARTED;
        
    }
    

       
       public void setCardDetails(String cardNumber, String cardHolderName, LocalDate expirationDate) throws PosException {
        if (cardNumber == null || cardHolderName == null || expirationDate == null) {
            throw new PosException("Card details cannot be null.");
        }
        if(expirationDate.isBefore(LocalDate.now()))
        {   this.status=PosApp.Status.DECLINED;
            throw new PosException("The credit card is expired");
        }
        this.cardNumber = cardNumber;
        this.cardHolderName = cardHolderName;
        this.expirationDate = expirationDate;
        this.status = PosApp.Status.READ;
    }

    

    
    public String getCardNumber() {
        return cardNumber;
    }


    public String processPayment(Issuer issuer, String pin) throws PosException {
        if (issuer == null){
            this.status=PosApp.Status.DECLINED;
            throw new PosException("Issuer s null");
        }
        TransactionResult result = issuer.validatePurchase(cardNumber, pin, amount);

        switch (result.getResultCode()) {
            case TransactionResult.OK:
                this.status =PosApp.Status.SUCCESS;
                this.transactionId = result.getId();
                return transactionId;

            case TransactionResult.WRONG_PIN:
                this.pinAttempts++ ;
                if (this.pinAttempts>=3) {
                    this.status =  PosApp.Status.DECLINED;
                } else{
                    this.status = PosApp.Status.WRONG_PIN;
                }
                throw new  PosException("Wrong  PIN ");
            default:
                this.status= PosApp.Status.DECLINED;
                throw new PosException("Transaction declined: " + result.getReason());
        }
    }

    public void setStatus(PosApp.Status status){
        this.status=status;
    }
    public PosApp.Status getStatus() {
        return status;
    }
    

  
       public double getAmount() {
        return amount;
    }

    public String getTransactionId() {
        return transactionId;
    }
    public int getPinAttempts() {
        return pinAttempts;
    }
}

