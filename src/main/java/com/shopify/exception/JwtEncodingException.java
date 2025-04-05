package com.shopify.exception;

/**
 * Exception thrown when there is an error encoding or decoding a JWT token.
 */
public class JwtEncodingException extends RuntimeException {

    public JwtEncodingException(String message) {
        super(message);
    }

    public JwtEncodingException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public JwtEncodingException(Throwable cause) {
        super("Error encoding or decoding JWT token", cause);
    }
}
