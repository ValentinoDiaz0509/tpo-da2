package com.healthgrid.monitoring.exception;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse (

    String key,

    String message,

    String path

) implements Serializable {}
