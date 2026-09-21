package com.swingtrade.api.service;

/** A run request named unknown symbols, variant ids or stages; mapped to HTTP 400. */
public class InvalidRunRequestException extends IllegalArgumentException {
    public InvalidRunRequestException(String message) {
        super(message);
    }
}
