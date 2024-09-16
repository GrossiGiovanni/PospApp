package it.polito.pos;

/**
 * Data class that contains the result of any
 * operation with the issuer server
 */
public class TransactionResult {

    /** Request was successfully completed */
    public static final int OK = 10;
    /** Payment request was declined. reason contains motivation */
    public static final int DECLINED = 20;
    /** Payment request not performed due to wrong PIN */
    public static final int WRONG_PIN = 21;
    /** Request not completed due to server timeout */
    public static final int TIMEOUT = 30;
    /** Request not completed due to internal server error */
    public static final int ERROR = 40;

    private final int code;
    private final String desc;

    /**
     * Creates a new transaction result object
     * 
     * @param code result code
     * @param desc transaction ID in case of success, reason otherwise
     */
    public TransactionResult(int code, String desc){
        this.code = code;
        this.desc = desc;
    }
    /**
     * Retrieves the result code of the transaction
     * 
     * Can be OK, DECLINED, WRONG_PIN, or TIMEOUT
     * 
     * @return result code
     */
    public int getResultCode(){
        return code;
    }

    /**
     * Retrieves the reason for any problem (not OK)
     * @return reason descriptiong
     */
    public String getReason() {
        if(code!=OK){
            return desc;
        }
        return null;
    }

    /**
     * Retrieves the transaction ID if result code is OK
     * @return transaction ID
     */
    public String getId(){
        if(code==OK){
            return desc;
        }
        return null;
    }
}
