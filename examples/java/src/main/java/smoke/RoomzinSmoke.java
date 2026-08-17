package smoke;

import com.roomzin.roomzinjava.client.RoomzinClient;
import com.roomzin.roomzinjava.client.RoomzinConfig;
import com.roomzin.roomzinjava.internal.protocol.ProtocolTypes;
import com.roomzin.roomzinjava.internal.protocol.RoomzinException;
import com.roomzin.roomzinjava.types.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

public class RoomzinSmoke {
    // ============================================================================
    // CONFIGURATION
    // ============================================================================

    private static final String MODE = "router";

    // Standalone configuration
    private static final String STANDALONE_HOST = "127.0.0.1";
    private static final int STANDALONE_PORT = 7777;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    // Cluster configuration (router address)
    private static final String ROUTER_HOST = "127.0.0.1";
    private static final int ROUTER_PORT = 9200;

    // Test data parameters - matches generator.py
    private static final int NUM_SEGMENTS = 4;
    private static final int NUM_PROPS_PER_SEGMENT = 10;
    private static final int NUM_ROOMS_PER_PROP = 4;
    private static final int NUM_DAYS = 10;
    private static final int SHARD_IDX = 1;

    // ============================================================================
    // TIMING HELPERS
    // ============================================================================

    static class StepTiming {
        String name;
        Duration duration;
        int requestCount;

        StepTiming(String name, Duration duration, int requestCount) {
            this.name = name;
            this.duration = duration;
            this.requestCount = requestCount;
        }
    }

    private static final List<StepTiming> timings = new ArrayList<>();

    private static void timeStep(String name, int requestCount, ThrowingRunnable fn) throws Exception {
        long start = System.nanoTime();
        try {
            fn.run();
            Duration duration = Duration.ofNanos(System.nanoTime() - start);
            timings.add(new StepTiming(name, duration, requestCount));
            System.out.printf("  ✅ %s completed in %dms (%d requests)%n", name, duration.toMillis(), requestCount);
        } catch (Exception e) {
            Duration duration = Duration.ofNanos(System.nanoTime() - start);
            timings.add(new StepTiming(name, duration, requestCount));
            System.out.printf("  ❌ %s failed after %dms%n", name, duration.toMillis());
            throw e;
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void printSummary() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  TIMING SUMMARY");
        System.out.println("=".repeat(60));

        Duration totalTime = Duration.ZERO;
        int totalRequests = 0;

        for (StepTiming t : timings) {
            totalTime = totalTime.plus(t.duration);
            totalRequests += t.requestCount;
            System.out.printf("  %-25s %10dms  %4d requests%n", t.name + ":", t.duration.toMillis(), t.requestCount);
        }

        System.out.println("-".repeat(60));
        System.out.printf("  %-25s %10dms  %4d requests%n", "TOTAL:", totalTime.toMillis(), totalRequests);
        System.out.printf("  %-25s %10dms%n", "Avg per request:", totalTime.dividedBy(totalRequests).toMillis());
        System.out.println("=".repeat(60));
    }

    @FunctionalInterface
    interface ThrowingSupplier {
        boolean get() throws Exception;
    }

    private static void waitForCondition(Duration timeout, ThrowingSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        Exception lastException = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition.get()) {
                    return;
                }
            } catch (Exception e) {
                lastException = e;
            }
            Thread.sleep(50);
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new Exception("Condition not met within " + timeout.toMillis() + "ms");
    }

    // ============================================================================
    // CLIENT CREATION
    // ============================================================================

    private static RoomzinClient createClient() throws RoomzinException {
        if (MODE.equals("standalone")) {
            RoomzinConfig cfg = RoomzinConfig.builder()
                    .withAddr(STANDALONE_HOST)
                    .withPort(STANDALONE_PORT)
                    .withMode(ProtocolTypes.Mode.STANDALONE)
                    .withTimeout(TIMEOUT)
                    .withKeepAlive(Duration.ofSeconds(30))
                    .build();
            return new RoomzinClient(cfg);
        }

        // Router mode
        RoomzinConfig cfg = RoomzinConfig.builder()
                .withAddr(ROUTER_HOST)
                .withPort(ROUTER_PORT)
                .withMode(ProtocolTypes.Mode.ROUTER)
                .withTimeout(Duration.ofSeconds(30))
                .withKeepAlive(Duration.ofSeconds(30))
                .build();
        return new RoomzinClient(cfg);
    }

    // ============================================================================
    // MAIN
    // ============================================================================

    public static void main(String[] args) {
        System.out.println("=== Roomzin API Example ===");
        System.out.printf("Mode: %s%n%n", MODE);

        try {
            // -------------------------------------------------------------------------
            // STEP 1: Connect to Roomzin
            // -------------------------------------------------------------------------
            System.out.println("[1/8] Connecting to Roomzin...");

            RoomzinClient client = createClient();
            System.out.printf("codecs %s%n", client.getCodecs().getRateFeatures());

            // -------------------------------------------------------------------------
            // STEP 2: Create properties and verify existence
            // -------------------------------------------------------------------------
            System.out.println("\n[2/8] SetProp...");

            timeStep("SetProp", NUM_SEGMENTS * NUM_PROPS_PER_SEGMENT, () -> {
                List<String> createdProps = new ArrayList<>();

                for (int s = 1; s <= NUM_SEGMENTS; s++) {
                    String segment = "segment_" + s;

                    for (int p = 1; p <= NUM_PROPS_PER_SEGMENT; p++) {
                        String propID = String.format("s%d_seg%d_p%d", SHARD_IDX, s, p);

                        double lat = 40.7128 + p * 0.001;
                        double lon = -74.0060 + p * 0.001;
                        List<String> amenities = Arrays.asList("wifi", "pool");

                        client.setProp(segment, SetPropPayload.builder()
                                .segment(segment)
                                .area("area_" + SHARD_IDX + "_" + s)
                                .propertyId(propID)
                                .propertyType("hotel")
                                .category("midrange")
                                .stars((short) 3)
                                .latitude(lat)
                                .longitude(lon)
                                .amenities(amenities)
                                .build());

                        createdProps.add(propID);
                    }
                }

                // check PropExist
                String p1 = createdProps.get(0);
                String segment = "segment_1";
                waitForCondition(Duration.ofSeconds(2), () -> {
                    return client.propExist(segment, p1);
                });

            });

            // -------------------------------------------------------------------------
            // STEP 3: Set room packages and verify rooms/dates
            // -------------------------------------------------------------------------
            System.out.println("\n[3/8] SetRoomPkg...");

            timeStep("SetRoomPkg", NUM_SEGMENTS * NUM_PROPS_PER_SEGMENT * NUM_ROOMS_PER_PROP * NUM_DAYS, () -> {
                List<String> dates = new ArrayList<>();
                for (int i = 0; i < NUM_DAYS; i++) {
                    LocalDate date = LocalDate.now().plusDays(i);
                    dates.add(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
                }

                for (int s = 1; s <= NUM_SEGMENTS; s++) {
                    String segment = "segment_" + s;

                    for (int p = 1; p <= NUM_PROPS_PER_SEGMENT; p++) {
                        String propID = String.format("s%d_seg%d_p%d", SHARD_IDX, s, p);

                        for (int r = 1; r <= NUM_ROOMS_PER_PROP; r++) {
                            String roomType = "room" + r;

                            for (String date : dates) {
                                short avail = (short) (10 + p);
                                int price = 100 + p * 10;
                                List<String> rateFeatures = Arrays.asList("free_cancellation", "free_wifi");

                                client.setRoomPkg(segment, SetRoomPkgPayload.builder()
                                        .propertyId(propID)
                                        .roomType(roomType)
                                        .date(date)
                                        .availability(avail)
                                        .finalPrice(price)
                                        .rateFeature(rateFeatures)
                                        .build());
                            }
                        }
                    }
                }

                String testProp = String.format("s%d_seg1_p1", SHARD_IDX);
                String segment = "segment_1";

                List<String> rooms = client.propRoomList(segment, testProp);
                List<String> expectedRooms = Arrays.asList("room1", "room2", "room3", "room4");
                if (rooms.size() != expectedRooms.size()) {
                    throw new Exception("expected " + expectedRooms.size() + " rooms, got " + rooms.size());
                }

                String testRoom = "room1";
                List<String> dateList = client.propRoomDateList(segment, PropRoomDateListPayload.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .build());

                if (dateList.size() != NUM_DAYS) {
                    throw new Exception("expected " + NUM_DAYS + " dates, got " + dateList.size());
                }
                System.out.println("        PropRoomDateList: " + dateList);

                client.getPropRoomDay(segment, GetRoomDayRequest.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .date(dates.get(0))
                        .build());
            });

            // -------------------------------------------------------------------------
            // STEP 4: Test SetRoomAvl, IncRoomAvl, DecRoomAvl
            // -------------------------------------------------------------------------
            System.out.println("\n[4/8] Update Availability...");

            timeStep("Update Availability", 4, () -> {
                String testDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                String testProp = String.format("s%d_seg1_p1", SHARD_IDX);
                String testRoom = "room1";
                String segment = "segment_1";

                GetRoomDayResult initial = client.getPropRoomDay(segment, GetRoomDayRequest.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .date(testDate)
                        .build());
                System.out.printf("        GetPropRoomDay: avail=%d, price=%d%n", initial.getAvailability(),
                        initial.getFinalPrice());

                short newAvail = 20;
                client.setRoomAvl(segment, UpdRoomAvlPayload.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .date(testDate)
                        .amount(newAvail)
                        .build());
                System.out.printf("        SetRoomAvl: %d → %d%n", initial.getAvailability(), newAvail);

                short incResult = client.incRoomAvl(segment, UpdRoomAvlPayload.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .date(testDate)
                        .amount((short) 1)
                        .build());
                System.out.printf("        IncRoomAvl: %d → %d%n", newAvail, incResult);

                short decResult = client.decRoomAvl(segment, UpdRoomAvlPayload.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .date(testDate)
                        .amount((short) 1)
                        .build());
                System.out.printf("        DecRoomAvl: %d → %d%n", incResult, decResult);
            });

            // -------------------------------------------------------------------------
            // STEP 5: Search availability and verify results
            // -------------------------------------------------------------------------
            System.out.println("\n[5/8] SearchAvail...");

            timeStep("SearchAvail", 1, () -> {
                String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                long limit = 100;
                int maxPrice = 150;

                List<PropertyAvail> results = client.searchAvail("segment_1", SearchAvailPayload.builder()
                        .segment("segment_1")
                        .roomType("room1")
                        .dates(Collections.singletonList(date))
                        .finalPrice(maxPrice)
                        .limit(limit)
                        .build());
                System.out.printf("        Found %d properties with max price %d%n", results.size(), maxPrice);
            });

            // -------------------------------------------------------------------------
            // STEP 6: Test deletion commands (in sequence)
            // -------------------------------------------------------------------------
            System.out.println("\n[6/8] Deletion commands...");

            timeStep("Deletion", 8, () -> {
                String segment = "segment_1";
                String testProp = String.format("s%d_seg1_p1", SHARD_IDX);
                String testRoom = "room1";
                String testDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

                System.out.println("        DelRoomDay...");
                client.delRoomDay(segment, DelRoomDayRequest.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .date(testDate)
                        .build());

                waitForCondition(Duration.ofSeconds(2), () -> {
                    List<String> dateList = client.propRoomDateList(segment, PropRoomDateListPayload.builder()
                            .propertyId(testProp)
                            .roomType(testRoom)
                            .build());
                    return !dateList.contains(testDate);
                });

                System.out.println("        DelPropRoom...");
                client.delPropRoom(segment, DelPropRoomPayload.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .build());

                waitForCondition(Duration.ofSeconds(2), () -> {
                    return !client.propRoomExist(segment, PropRoomExistPayload.builder()
                            .propertyId(testProp)
                            .roomType(testRoom)
                            .build());
                });

                System.out.println("        DelProp...");
                client.delProp(segment, testProp);

                waitForCondition(Duration.ofSeconds(2), () -> {
                    return !client.propExist(segment, testProp);
                });

                System.out.println("        DelSegment...");
                client.delSegment("segment_1");

                waitForCondition(Duration.ofSeconds(2), () -> {
                    List<String> props = client.searchProp("segment_1", SearchPropPayload.builder()
                            .segment("segment_1")
                            .build());
                    return props.isEmpty();
                });
            });

            // -------------------------------------------------------------------------
            // STEP 7: Clean up remaining data
            // -------------------------------------------------------------------------
            System.out.println("\n[7/8] Cleaning up...");

            timeStep("Cleanup", 3, () -> {
                for (int s = 2; s <= NUM_SEGMENTS; s++) {
                    String seg = "segment_" + s;
                    try {
                        client.delSegment(seg);
                        System.out.println("        Cleaned up " + seg);
                    } catch (RoomzinException e) {
                        System.out.println("Warning: Failed to delete " + seg + ": " + e.getMessage());
                    }
                }
            });

            printSummary();
            System.out.println("\n✅ All completed successfully!");

            client.close();

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}