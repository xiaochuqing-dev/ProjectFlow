package com.projectflow.service.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.projectflow.entity.ModelProtocol;

@Component
public class ModelProtocolAdapterRegistry {
    private final Map<ModelProtocol, ModelProtocolAdapter> adapters = new EnumMap<>(ModelProtocol.class);

    public ModelProtocolAdapterRegistry(List<ModelProtocolAdapter> available) {
        available.forEach(adapter -> adapters.put(adapter.protocol(), adapter));
    }

    public ModelProtocolAdapter require(ModelProtocol protocol) {
        ModelProtocolAdapter adapter = adapters.get(protocol);
        if (adapter == null) throw new IllegalStateException("No model protocol adapter registered for " + protocol);
        return adapter;
    }
}
