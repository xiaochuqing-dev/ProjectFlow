package com.projectflow.service.model;

import java.io.IOException;

import com.projectflow.entity.ModelProtocol;

public interface ModelProtocolAdapter {
    ModelProtocol protocol();
    CanonicalModelResponse execute(CanonicalModelRequest request) throws IOException;
}
