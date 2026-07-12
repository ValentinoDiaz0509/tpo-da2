package com.healthgrid.monitoring.exception;

import lombok.Getter;

@Getter
public class ApplicationException extends RuntimeException {

    private final ExceptionEnum exceptionEnum;
    private final String key;
    private final String message;

    public ApplicationException(ExceptionEnum exceptionEnum) {
        this.exceptionEnum = exceptionEnum;
        this.key = exceptionEnum.getKey();
        this.message = exceptionEnum.getMessage();
    }

    public ApplicationException(ExceptionEnum exceptionEnum, String message) {
        this.exceptionEnum = exceptionEnum;
        this.key = exceptionEnum.getKey();
        this.message = String.format(exceptionEnum.getMessage(), message);
    }
}
