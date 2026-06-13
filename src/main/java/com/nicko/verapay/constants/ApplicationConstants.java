package com.nicko.verapay.constants;

public class ApplicationConstants {

    private ApplicationConstants() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    public static final String JWT_SECRET_KEY = "JWT_SECRET";
    public static final String JWT_SECRET_DEFAULT_VALUE = "jxgEQeXHuPq8VdbyYFNkANdudQ53YUn4";
    public static final String JWT_HEADER = "Authorization";


    public static final String ACTIVE_STATUS = "ACTIVE";

    public static final String  NEW_MESSAGE = "NEW";
    public static final String  CLOSED_MESSAGE = "CLOSED";

    public static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    public static final String  SYSTEM = "SYSTEM";

    // Transaction Types
    public static final String TRANSACTION_TYPE_DEPOSIT  = "DEPOSIT";
    public static final String TRANSACTION_TYPE_WITHDRAW = "WITHDRAW";
    public static final String TRANSACTION_TYPE_TRANSFER = "TRANSFER";

    // Transaction Statuses
    public static final String TRANSACTION_STATUS_PENDING = "PENDING";
    public static final String TRANSACTION_STATUS_SUCCESS = "SUCCESS";
    public static final String TRANSACTION_STATUS_FAILED  = "FAILED";

    // Ledger Entry Types
    public static final String LEDGER_DEBIT  = "DEBIT";
    public static final String LEDGER_CREDIT = "CREDIT";

//    Transaction reversal
    public static final String TRANSACTION_STATUS_REVERSED = "REVERSED";
    public static final String TRANSACTION_TYPE_REVERSAL = "REVERSAL";



}
