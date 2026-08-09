package com.example.shortener.exception;

public class AliasAlreadyExistsException extends RuntimeException {

    public AliasAlreadyExistsException(String alias) {
        super("alias '" + alias + "' is already taken");
    }
}