package net.inkyquill.delightfulkitchen.kitchen.station;

import net.inkyquill.delightfulkitchen.kitchen.recipe.KitchenStationType;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class KitchenStationService {
    private final List<KitchenStationEndpoint> endpoints;

    public KitchenStationService(List<KitchenStationEndpoint> endpoints) {
        this.endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints"));
    }

    public List<KitchenStationEndpoint> endpoints() {
        return endpoints;
    }

    public Optional<KitchenStationEndpoint> firstAccepting(KitchenStationJob job) {
        return endpoints.stream()
                .filter(endpoint -> endpoint.station().stationType() == job.stationType())
                .filter(endpoint -> endpoint.station().canAccept(job).accepted())
                .min(Comparator
                        .comparingInt((KitchenStationEndpoint endpoint) -> endpoint.station().queuedJobCount())
                        .thenComparing(endpoint -> endpoint.pos().asLong()));
    }

    public boolean hasStation(KitchenStationType type) {
        return endpoints.stream().anyMatch(endpoint -> endpoint.station().stationType() == type);
    }
}
