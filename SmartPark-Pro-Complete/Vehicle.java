import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/** Represents one vehicle currently parked in SmartPark Pro. */
public class Vehicle {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());

    final String plate;
    final String type;
    final String owner;
    final String phone;
    final String slot;
    final Instant entryTime;

    public Vehicle(String plate, String type, String owner, String phone, String slot) {
        this.plate = plate;
        this.type = type;
        this.owner = owner;
        this.phone = phone;
        this.slot = slot;
        this.entryTime = Instant.now();
    }

    public long minutesParked() {
        return Math.max(1, Duration.between(entryTime, Instant.now()).toMinutes());
    }

    /** ₹30 per started hour, with a minimum fee of ₹20. */
    public double fee() {
        return Math.max(20, Math.ceil(minutesParked() / 60.0) * 30.0);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plate", plate);
        result.put("type", type);
        result.put("owner", owner);
        result.put("phone", phone);
        result.put("slot", slot);
        result.put("entryTime", TIME.format(entryTime));
        result.put("minutes", minutesParked());
        result.put("estimatedFee", fee());
        return result;
    }
}
