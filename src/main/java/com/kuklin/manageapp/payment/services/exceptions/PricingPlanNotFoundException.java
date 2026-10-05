package com.kuklin.manageapp.payment.services.exceptions;

public class PricingPlanNotFoundException extends Exception{
    public static String DEF_MSG = "Pricing plan not found!";
    public PricingPlanNotFoundException() {
        super(DEF_MSG);
    }
    public PricingPlanNotFoundException(String message) {
        super(message);
    }

    public PricingPlanNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
