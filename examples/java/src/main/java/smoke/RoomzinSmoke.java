package smoke;

import com.roomzin.roomzinjava.api.CacheClientApi;
import com.roomzin.roomzinjava.client.cluster.ClusterClient;
import com.roomzin.roomzinjava.client.cluster.ClusterConfig;
import com.roomzin.roomzinjava.client.single.SingleClient;
import com.roomzin.roomzinjava.client.single.SingleConfig;
import com.roomzin.roomzinjava.internal.protocol.RoomzinException;
import com.roomzin.roomzinjava.types.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

// ============================================================================
// CONFIGURATION
// ============================================================================

/**
 * Roomzin Smoke Test - API Usage Example & Implicit Test
 * 
 * This file serves two purposes:
 * 1. Demonstrates how to use the Roomzin Java SDK
 * 2. Acts as an implicit integration test for Roomzin servers
 * 
 * To run: mvn exec:java
 */
public class RoomzinSmoke {
    // ============================================================================
    // CONFIGURATION - Change these to match your environment
    // ============================================================================

    // Change this to "standalone" to test against a single Roomzin instance
    private static final String MODE = "standalone";

    // Standalone configuration
    private static final String STANDALONE_HOST = "127.0.0.1";
    private static final int STANDALONE_PORT = 7777;
    private static final String TOKEN = "abc123";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    // Cluster configuration (update these IPs to match your cluster)
    private static final List<NodeAddr> STATIC_DISCOVERY = Arrays.asList(
            new NodeAddr("roomzin-0", "172.20.0.10", 7777, 8080),
            new NodeAddr("roomzin-1", "172.20.0.11", 7777, 8080),
            new NodeAddr("roomzin-2", "172.20.0.12", 7777, 8080));

    // Test data parameters
    private static final int NUM_SEGMENTS = 2;
    private static final int NUM_PROPS_PER_SEGMENT = 1000;
    private static final int NUM_ROOMS_PER_PROP = 2;
    private static final int NUM_DAYS = 3;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ============================================================================
    // CLIENT CREATION
    // ============================================================================

    private static CacheClientApi createClient() throws RoomzinException {
        if ("standalone".equalsIgnoreCase(MODE)) {
            return getStandaloneClient();
        }
        return getClusterClient();
    }

    private static CacheClientApi getStandaloneClient() throws RoomzinException {
        SingleConfig config = SingleConfig.builder()
                .withHost(STANDALONE_HOST)
                .withTcpPort(STANDALONE_PORT)
                .withAuthToken(TOKEN)
                .withTimeout(TIMEOUT)
                .build();
        return new SingleClient(config);
    }

    private static CacheClientApi getClusterClient() throws RoomzinException {
        ClusterConfig config = ClusterConfig.builder()
                .withSeedNodeIds("roomzin-0,roomzin-1,roomzin-2")
                .withStaticDiscovery(STATIC_DISCOVERY)
                .withTcpPort(7777)
                .withApiPort(8080)
                .withAuthToken(TOKEN)
                .withTimeout(Duration.ofSeconds(30))
                .build();
        return new ClusterClient(config);
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    private static List<String> generateDates(int count) {
        List<String> dates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            dates.add(LocalDate.now().plusDays(i + 1).format(DATE_FORMATTER));
        }
        return dates;
    }

    private static void waitForCondition(Duration timeout, Condition condition) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.check()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new RuntimeException("Condition not met within " + timeout);
    }

    @FunctionalInterface
    private interface Condition {
        boolean check() throws Exception;
    }

    // ============================================================================
    // MAIN - CLEAR LINEAR FLOW
    // ============================================================================

    public static void main(String[] args) {
        System.out.println("=== Roomzin API Example ===");
        System.out.println("Mode: " + MODE);
        System.out.println();

        try {
            // -------------------------------------------------------------------------
            // STEP 1: Connect to Roomzin
            // -------------------------------------------------------------------------
            System.out.println("[1/8] Connecting to Roomzin...");

            try (CacheClientApi client = createClient()) {
                System.out.println("  Connected successfully!");

                // -------------------------------------------------------------------------
                // STEP 2: Create properties and verify existence
                // -------------------------------------------------------------------------
                System.out.println("[2/8] SetProp...");

                List<String> createdProps = new ArrayList<>();
                List<String> dates = generateDates(NUM_DAYS);

                for (int s = 1; s <= NUM_SEGMENTS; s++) {
                    String segment = "seg_" + s;
                    for (int p = 1; p <= NUM_PROPS_PER_SEGMENT; p++) {
                        String propId = "seg_" + s + "_p" + p;

                        SetPropPayload prop = SetPropPayload.builder()
                                .segment(segment)
                                .area("area_1")
                                .propertyId(propId)
                                .propertyType("hotel")
                                .category("midrange")
                                .stars((short) 3)
                                .latitude(40.7128 + p * 0.001)
                                .longitude(-74.0060 + p * 0.001)
                                .amenities(Arrays.asList("wifi", "pool"))
                                .build();

                        client.setProp(prop);
                        createdProps.add(propId);
                    }
                }

                // Check PropExist
                String p1 = createdProps.get(createdProps.size() - 1);
                waitForCondition(Duration.ofSeconds(2), () -> client.propExist(p1));
                System.out.println("  ✓ All properties created, verified " + p1 + " exists");

                // -------------------------------------------------------------------------
                // STEP 3: Set room packages and verify rooms/dates
                // -------------------------------------------------------------------------
                System.out.println("[3/8] SetRoomPkg...");

                for (int s = 1; s <= NUM_SEGMENTS; s++) {
                    for (int p = 1; p <= NUM_PROPS_PER_SEGMENT; p++) {
                        String propId = "seg_" + s + "_p" + p;

                        for (int r = 1; r <= NUM_ROOMS_PER_PROP; r++) {
                            String roomType = "room_" + r;

                            for (String date : dates) {
                                short avail = (short) (10 + p);
                                int price = 100 + p * 10;
                                List<String> rateFeatures = Arrays.asList("free_cancellation", "free_wifi");

                                SetRoomPkgPayload roomPkg = SetRoomPkgPayload.builder()
                                        .propertyId(propId)
                                        .roomType(roomType)
                                        .date(date)
                                        .availability(avail)
                                        .finalPrice(price)
                                        .rateFeature(rateFeatures)
                                        .build();

                                client.setRoomPkg(roomPkg);
                            }
                        }
                    }
                }

                // Verify room lists for first property
                String testProp = "seg_1_p1";
                List<String> rooms = client.propRoomList(testProp);
                List<String> expectedRooms = Arrays.asList("room_1", "room_2");
                Collections.sort(rooms);
                Collections.sort(expectedRooms);

                if (!rooms.equals(expectedRooms)) {
                    throw new RuntimeException("Expected " + expectedRooms + " rooms, got " + rooms);
                }
                System.out.println("  ✓ Room list verified: " + rooms);

                // Verify date lists for first room
                String testRoom = "room_1";
                PropRoomDateListPayload dateListPayload = PropRoomDateListPayload.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .build();
                List<String> dateList = client.propRoomDateList(dateListPayload);

                if (dateList.size() != NUM_DAYS) {
                    throw new RuntimeException("Expected " + NUM_DAYS + " dates, got " + dateList.size());
                }
                System.out.println("  ✓ Date list verified: " + dateList);

                // Spot check: get a specific room/day
                GetRoomDayRequest spotRequest = GetRoomDayRequest.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .date(dates.get(0))
                        .build();
                GetRoomDayResult spotCheck = client.getPropRoomDay(spotRequest);
                System.out.println("  ✓ Spot check: room/day exists with avail=" + spotCheck.getAvailability() +
                        ", price=" + spotCheck.getFinalPrice());

                // -------------------------------------------------------------------------
                // STEP 4: Test SetRoomAvl, IncRoomAvl, DecRoomAvl
                // -------------------------------------------------------------------------
                System.out.println("[4/8] Update Availability...");

                String testDate = dates.get(0);

                // Get initial availability
                GetRoomDayRequest getRequest = GetRoomDayRequest.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .date(testDate)
                        .build();
                GetRoomDayResult initial = client.getPropRoomDay(getRequest);
                System.out.println("  GetPropRoomDay: avail=" + initial.getAvailability() +
                        ", price=" + initial.getFinalPrice());

                // SetRoomAvl
                short newAvail = 20;
                UpdRoomAvlPayload setPayload = UpdRoomAvlPayload.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .date(testDate)
                        .amount(newAvail)
                        .build();
                short setResult = client.setRoomAvl(setPayload);
                System.out.println("  SetRoomAvl: " + initial.getAvailability() + " → " + setResult);

                // IncRoomAvl
                UpdRoomAvlPayload incPayload = UpdRoomAvlPayload.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .date(testDate)
                        .amount((short) 1)
                        .build();
                short incResult = client.incRoomAvl(incPayload);
                System.out.println("  IncRoomAvl: " + newAvail + " → " + incResult);

                // DecRoomAvl
                UpdRoomAvlPayload decPayload = UpdRoomAvlPayload.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .date(testDate)
                        .amount((short) 1)
                        .build();
                short decResult = client.decRoomAvl(decPayload);
                System.out.println("  DecRoomAvl: " + incResult + " → " + decResult);

                // -------------------------------------------------------------------------
                // STEP 5: Search availability and verify results
                // -------------------------------------------------------------------------
                System.out.println("[5/8] SearchAvail...");

                long limit = 100;
                int maxPrice = 150;

                SearchAvailPayload searchPayload = SearchAvailPayload.builder()
                        .segment("seg_1")
                        .roomType("room_1")
                        .dates(Arrays.asList(dates.get(0)))
                        .finalPrice(maxPrice)
                        .limit(limit)
                        .build();

                List<PropertyAvail> results = client.searchAvail(searchPayload);
                System.out.println("  Found " + results.size() + " properties with max price " + maxPrice);

                if (results.isEmpty()) {
                    throw new RuntimeException("Expected at least one search result");
                }

                // -------------------------------------------------------------------------
                // STEP 6: Test deletion commands (in sequence)
                // -------------------------------------------------------------------------
                System.out.println("[6/8] Deletion commands...");

                // Create final copies for lambdas
                final String finalTestProp = testProp;
                final String finalTestRoom = testRoom;
                final String finalTestDate = testDate;

                // 6.1: DelRoomDay
                System.out.println("  DelRoomDay...");
                DelRoomDayRequest delRoomDayRequest = DelRoomDayRequest.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .date(testDate)
                        .build();
                client.delRoomDay(delRoomDayRequest);

                // Verify date was removed
                waitForCondition(Duration.ofSeconds(2), () -> {
                    PropRoomDateListPayload checkPayload = PropRoomDateListPayload.builder()
                            .propertyId(finalTestProp)
                            .roomType(finalTestRoom)
                            .build();
                    List<String> updatedDateList = client.propRoomDateList(checkPayload);
                    return !updatedDateList.contains(finalTestDate);
                });
                System.out.println("  ✓ Date removed successfully");

                // 6.2: DelPropRoom
                System.out.println("  DelPropRoom...");
                DelPropRoomPayload delPropRoomPayload = DelPropRoomPayload.builder()
                        .propertyId(testProp)
                        .roomType(testRoom)
                        .build();
                client.delPropRoom(delPropRoomPayload);

                // Verify room was removed
                waitForCondition(Duration.ofSeconds(2), () -> {
                    PropRoomExistPayload existPayload = PropRoomExistPayload.builder()
                            .propertyId(finalTestProp)
                            .roomType(finalTestRoom)
                            .build();
                    boolean exists = client.propRoomExist(existPayload);
                    return !exists;
                });
                System.out.println("  ✓ Room removed successfully");

                // 6.3: DelProp
                System.out.println("  DelProp...");
                client.delProp(testProp);

                // Verify property was removed
                waitForCondition(Duration.ofSeconds(2), () -> {
                    boolean exists = client.propExist(finalTestProp);
                    return !exists;
                });
                System.out.println("  ✓ Property removed successfully");

                // 6.4: DelSegment
                System.out.println("  DelSegment...");
                client.delSegment("seg_1");

                // Verify segment was removed
                waitForCondition(Duration.ofSeconds(2), () -> {
                    SearchPropPayload searchPropPayload = SearchPropPayload.builder()
                            .segment("seg_1")
                            .build();
                    List<String> props = client.searchProp(searchPropPayload);
                    return props.isEmpty();
                });
                System.out.println("  ✓ Segment removed successfully");

                // -------------------------------------------------------------------------
                // STEP 7: Clean up remaining data
                // -------------------------------------------------------------------------
                System.out.println("[7/7] Cleaning up...");

                try {
                    client.delSegment("seg_2");
                    System.out.println("  Cleaned up seg_2");
                } catch (RoomzinException e) {
                    System.out.println("  Warning: Failed to delete seg_2: " + e.getMessage());
                }

                System.out.println();
                System.out.println("✅ All completed successfully!");
                client.close();
                System.exit(0);

            } catch (Exception e) {
                System.err.println("❌ Test failed: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("❌ Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}