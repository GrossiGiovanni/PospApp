package it.polito.pos;

public interface Issuer {

    TransactionResult validatePurchase(String cardNumber, String pin, double amount);

    TransactionResult cancelPurchase(String tid, double amount);

}
