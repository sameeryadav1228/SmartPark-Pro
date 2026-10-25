import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

/** Contains parking rules, data structures, reporting and API operations. */
public class ParkingService {
    private static final int SLOT_COUNT = 30;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());

    private final Map<String, Vehicle> parkedVehicles = new LinkedHashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();
    private final TreeSet<Integer> freeSlots = new TreeSet<>();
    private final Queue<Map<String, String>> waitingQueue = new ArrayDeque<>();
    private double totalRevenue = 0;
    private int nextRecordId = 1;

    public ParkingService() { resetSlots(); }

    public synchronized void handleApi(HttpExchange exchange, String path, String method) throws IOException {
        if (path.equals("/api/state") && method.equals("GET")) {
            HttpUtils.sendJson(exchange, 200, state());
        } else if (path.equals("/api/register") && method.equals("POST")) {
            registerVehicle(exchange);
        } else if (path.equals("/api/exit") && method.equals("POST")) {
            exitVehicle(exchange);
        } else if (path.equals("/api/search") && method.equals("GET")) {
            searchVehicles(exchange);
        } else if (path.equals("/api/export") && method.equals("GET")) {
            exportCsv(exchange);
        } else if (path.equals("/api/reset-demo") && method.equals("POST")) {
            resetDemo(exchange);
        } else {
            HttpUtils.sendJson(exchange, 404, message(false, "API route not found."));
        }
    }

    private void registerVehicle(HttpExchange exchange) throws IOException {
        Map<String, String> form = HttpUtils.readForm(exchange);
        String plate = HttpUtils.clean(form.get("plate")).toUpperCase(Locale.ROOT);
        String type = HttpUtils.clean(form.get("type"));
        String owner = HttpUtils.clean(form.get("owner"));
        String phone = HttpUtils.clean(form.get("phone"));

        if (plate.isBlank() || type.isBlank()) {
            HttpUtils.sendJson(exchange, 400, message(false, "Registration number and vehicle type are required.")); return;
        }
        if (!plate.matches("[A-Z0-9 -]{4,15}")) {
            HttpUtils.sendJson(exchange, 400, message(false, "Enter a valid registration number (4–15 letters or numbers).")); return;
        }
        if (parkedVehicles.containsKey(plate) || isWaiting(plate)) {
            HttpUtils.sendJson(exchange, 409, message(false, "This vehicle is already parked or waiting.")); return;
        }
        if (freeSlots.isEmpty()) {
            Map<String, String> waiting = new LinkedHashMap<>();
            waiting.put("plate", plate); waiting.put("type", type); waiting.put("owner", owner); waiting.put("phone", phone);
            waitingQueue.add(waiting);
            addHistory("WAITLIST", plate, type, "Waiting queue", 0);
            HttpUtils.sendJson(exchange, 200, message(true, "Parking is full. Vehicle added to the waiting queue.")); return;
        }
        int slotNumber = freeSlots.pollFirst();
        String slot = String.format(Locale.ROOT, "P-%02d", slotNumber);
        Vehicle vehicle = new Vehicle(plate, type, owner, phone, slot);
        parkedVehicles.put(plate, vehicle);
        addHistory("ENTRY", plate, type, slot, 0);
        HttpUtils.sendJson(exchange, 200, Map.of("success", true, "message", "Vehicle registered successfully.", "vehicle", vehicle.toMap()));
    }

    private void exitVehicle(HttpExchange exchange) throws IOException {
        Map<String, String> form = HttpUtils.readForm(exchange);
        String plate = HttpUtils.clean(form.get("plate")).toUpperCase(Locale.ROOT);
        Vehicle vehicle = parkedVehicles.remove(plate);
        if (vehicle == null) {
            HttpUtils.sendJson(exchange, 404, message(false, "Vehicle not found in the parking area.")); return;
        }
        double fee = vehicle.fee();
        totalRevenue += fee;
        freeSlots.add(Integer.parseInt(vehicle.slot.substring(2)));
        addHistory("EXIT", plate, vehicle.type, vehicle.slot, fee);
        String note = "Vehicle checked out. Fee: ₹" + String.format(Locale.ROOT, "%.2f", fee);

        if (!waitingQueue.isEmpty()) {
            Map<String, String> waiting = waitingQueue.poll();
            int assigned = freeSlots.pollFirst();
            Vehicle next = new Vehicle(waiting.get("plate"), waiting.get("type"), waiting.get("owner"), waiting.get("phone"),
                    String.format(Locale.ROOT, "P-%02d", assigned));
            parkedVehicles.put(next.plate, next);
            addHistory("ENTRY", next.plate, next.type, next.slot, 0);
            note += ". Next waiting vehicle " + next.plate + " assigned to " + next.slot + ".";
        }
        HttpUtils.sendJson(exchange, 200, Map.of("success", true, "message", note, "fee", fee));
    }

    private void searchVehicles(HttpExchange exchange) throws IOException {
        String query = HttpUtils.readQuery(exchange).getOrDefault("q", "").toUpperCase(Locale.ROOT);
        List<Object> matches = new ArrayList<>();
        for (Vehicle vehicle : parkedVehicles.values()) {
            if (vehicle.plate.contains(query) || vehicle.owner.toUpperCase(Locale.ROOT).contains(query)) matches.add(vehicle.toMap());
        }
        HttpUtils.sendJson(exchange, 200, Map.of("success", true, "results", matches));
    }

    private Map<String, Object> state() {
        List<Object> vehicles = new ArrayList<>();
        for (Vehicle vehicle : parkedVehicles.values()) vehicles.add(vehicle.toMap());
        List<Integer> slots = new ArrayList<>();
        for (int i = 1; i <= SLOT_COUNT; i++) slots.add(i);
        List<Object> historyCopy = new ArrayList<>(history);
        List<Object> waitingCopy = new ArrayList<>();
        for (Map<String, String> item : waitingQueue) waitingCopy.add(item);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true); result.put("totalSlots", SLOT_COUNT); result.put("available", freeSlots.size());
        result.put("occupied", parkedVehicles.size()); result.put("vehicles", vehicles); result.put("history", historyCopy);
        result.put("waiting", waitingCopy); result.put("revenue", totalRevenue); result.put("slots", slots);
        result.put("serverTime", TIME.format(Instant.now()));
        return result;
    }

    private void addHistory(String action, String plate, String type, String slot, double fee) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", nextRecordId++); record.put("time", TIME.format(Instant.now())); record.put("action", action);
        record.put("plate", plate); record.put("type", type); record.put("slot", slot); record.put("fee", fee);
        history.add(0, record);
        if (history.size() > 500) history.remove(history.size() - 1);
    }

    private void exportCsv(HttpExchange exchange) throws IOException {
        StringBuilder csv = new StringBuilder("Record ID,Date and Time,Action,Registration,Vehicle Type,Slot,Fee (INR)\r\n");
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> record = history.get(i);
            csv.append(csvCell(record.get("id"))).append(',').append(csvCell(record.get("time"))).append(',')
               .append(csvCell(record.get("action"))).append(',').append(csvCell(record.get("plate"))).append(',')
               .append(csvCell(record.get("type"))).append(',').append(csvCell(record.get("slot"))).append(',')
               .append(csvCell(record.get("fee"))).append("\r\n");
        }
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"smartpark-report.csv\"");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private String csvCell(Object value) {
        String text = String.valueOf(value == null ? "" : value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private void resetDemo(HttpExchange exchange) throws IOException {
        parkedVehicles.clear(); history.clear(); waitingQueue.clear(); freeSlots.clear(); resetSlots();
        totalRevenue = 0; nextRecordId = 1;
        HttpUtils.sendJson(exchange, 200, message(true, "Demo data cleared."));
    }

    private void resetSlots() { for (int i = 1; i <= SLOT_COUNT; i++) freeSlots.add(i); }
    private boolean isWaiting(String plate) { for (Map<String, String> item : waitingQueue) if (plate.equals(item.get("plate"))) return true; return false; }
    private Map<String, Object> message(boolean success, String message) { return Map.of("success", success, "message", message); }
}
