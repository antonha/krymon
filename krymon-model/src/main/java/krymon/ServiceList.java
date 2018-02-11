package krymon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public class ServiceList {
    private final List<Service> services;

    @JsonCreator
    public ServiceList(@JsonProperty("services") List<Service> services) {
        this.services = services;
    }

    public List<Service> getServices() {
        return services;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceList that = (ServiceList) o;
        return Objects.equals(services, that.services);
    }

    @Override
    public int hashCode() {
        return Objects.hash(services);
    }
}
