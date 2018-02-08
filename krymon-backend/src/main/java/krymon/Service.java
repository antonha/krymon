package krymon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.joda.time.DateTime;

import java.util.Objects;

public class Service {
    private final String id;
    private final String name;
    private final String url;
    private final Status status;
    private final DateTime dateTime;

    @JsonCreator
    public Service(
            @JsonProperty("id")
            String id,
            @JsonProperty("name")
            String name,
            @JsonProperty("url")
            String url,
            @JsonProperty("status")
            Status status,
            @JsonProperty("dateTime")
            DateTime dateTime
    ) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.status = status;
        this.dateTime = dateTime;
    }

    public enum Status{
        OK, FAIL, UNKNOWN
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public Status getStatus() {
        return status;
    }

    public DateTime getDateTime() {
        return dateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Service service = (Service) o;
        return Objects.equals(id, service.id) &&
                Objects.equals(name, service.name) &&
                Objects.equals(url, service.url) &&
                status == service.status &&
                Objects.equals(dateTime, service.dateTime);
    }

    @Override
    public int hashCode() {

        return Objects.hash(id, name, url, status, dateTime);
    }
}
