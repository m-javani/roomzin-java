# Roomzin Java SDK

Official Java SDK for [Roomzin](https://m-javani.github.io/roomzin-doc/) — a high-performance in-memory inventory engine for booking platforms.

The SDK provides a clean, idiomatic Java interface for communicating with Roomzin servers in both standalone and clustered deployments. It automatically handles connection management, request/response demuxing, and self-healing reconnections.

---

## Features

- Unified client for standalone and router (cluster) modes
- Built-in connection self-healing
- Automatic request routing (writes to leader, reads to followers) via router
- Fully typed Java API
- AutoCloseable client for resource management
- Type-safe API with segment support

---

## Requirements

- Java 11 or later
- Roomzin Server v1.x
- Roomzin Router (for cluster mode)

---

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.m-javani</groupId>
    <artifactId>roomzin-java</artifactId>
    <version>2.0.0</version>
</dependency>
```

### Gradle

```gradle
implementation 'io.github.m-javani:roomzin-java:2.0.0'
```

---

## Client Setup

### Standalone Mode

Connect directly to a standalone Roomzin server:

```java
import com.roomzin.roomzinjava.client.RoomzinClient;
import com.roomzin.roomzinjava.client.RoomzinConfig;
import com.roomzin.roomzinjava.internal.protocol.Mode;
import java.time.Duration;

RoomzinConfig config = RoomzinConfig.builder()
    .addr("127.0.0.1")
    .port(7777)
    .mode(Mode.STANDALONE)
    .timeout(Duration.ofSeconds(5))
    .keepAlive(Duration.ofSeconds(30))
    .build();

RoomzinClient client = new RoomzinClient(config);
client.connect();

// Use client...
client.close();
```

### Cluster Mode (via Router)

Connect to a Roomzin cluster through the router:

```java
import com.roomzin.roomzinjava.client.RoomzinClient;
import com.roomzin.roomzinjava.client.RoomzinConfig;
import com.roomzin.roomzinjava.internal.protocol.Mode;
import java.time.Duration;

RoomzinConfig config = RoomzinConfig.builder()
    .addr("router.example.com")
    .port(9200)
    .mode(Mode.ROUTER)
    .timeout(Duration.ofSeconds(30))
    .keepAlive(Duration.ofSeconds(30))
    .build();

RoomzinClient client = new RoomzinClient(config);
client.connect();
client.close();
```

---

## Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `addr()` | Server or router address | Required |
| `port()` | TCP port | Required |
| `mode()` | `Mode.STANDALONE` or `Mode.ROUTER` | `Mode.STANDALONE` |
| `timeout()` | Request timeout | 2s |
| `keepAlive()` | TCP keep-alive interval | 30s |

---

## Segment Routing

In cluster mode, every request must specify a segment. The router uses this to route the request to the correct shard.

```java
String segment = "us-east";

// All API methods accept segment as a parameter
client.setProp(segment, payload);
```

In standalone mode, the segment parameter is ignored but still required for API compatibility. This allows you to switch between standalone and cluster modes without changing your business logic.

---

## Property Management

### setProp
Adds or updates a property.

```java
client.setProp("downtown", SetPropPayload.builder()
    .segment("downtown")
    .area("manhattan")
    .propertyId("hotel_123")
    .propertyType("hotel")
    .category("luxury")
    .stars((short) 4)
    .latitude(40.7128)
    .longitude(-74.0060)
    .amenities(Arrays.asList("wifi", "pool", "gym"))
    .build());
```

### searchProp
Searches properties by segment, area, type, or location.

```java
// By segment
List<String> ids = client.searchProp("downtown", SearchPropPayload.builder()
    .segment("downtown")
    .build());

// By area
List<String> ids = client.searchProp("downtown", SearchPropPayload.builder()
    .segment("downtown")
    .area("manhattan")
    .build());

// By location (radius search)
List<String> ids = client.searchProp("downtown", SearchPropPayload.builder()
    .segment("downtown")
    .latitude(40.7128)
    .longitude(-74.0060)
    .build());
```

### propExist
Checks if a property exists.

```java
boolean exists = client.propExist("downtown", "hotel_123");
```

### propRoomExist
Checks if a specific room type exists for a property.

```java
boolean exists = client.propRoomExist("downtown", PropRoomExistPayload.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .build());
```

### propRoomList
Lists all room types for a property.

```java
List<String> rooms = client.propRoomList("downtown", "hotel_123");
```

### propRoomDateList
Lists dates with availability data for a property and room type.

```java
List<String> dates = client.propRoomDateList("downtown", PropRoomDateListPayload.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .build());
```

---

## Room Package Management

### setRoomPkg
Sets availability, price, and rate features for a room type on a date.

```java
client.setRoomPkg("downtown", SetRoomPkgPayload.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .date("2026-07-20")
    .availability((short) 10)
    .finalPrice(199)
    .rateFeature(Arrays.asList("free_cancellation", "breakfast_included"))
    .build());
```

### setRoomAvl
Sets exact availability for a room type on a specific date.

```java
short newAvail = client.setRoomAvl("downtown", UpdRoomAvlPayload.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .date("2026-07-20")
    .amount((short) 20)
    .build());
```

### incRoomAvl
Increases availability (e.g., on cancellation).

```java
short newAvail = client.incRoomAvl("downtown", UpdRoomAvlPayload.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .date("2026-07-20")
    .amount((short) 1)
    .build());
```

### decRoomAvl
Decreases availability (e.g., on booking).

```java
short newAvail = client.decRoomAvl("downtown", UpdRoomAvlPayload.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .date("2026-07-20")
    .amount((short) 2)
    .build());
```

### getPropRoomDay
Gets availability and pricing for a specific room on a specific date.

```java
GetRoomDayResult day = client.getPropRoomDay("downtown", GetRoomDayRequest.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .date("2026-07-20")
    .build());
System.out.println("Avail: " + day.getAvailability() + ", Price: " + day.getFinalPrice());
```

---

## Search & Query

### searchAvail
Searches available rooms by filters.

```java
List<PropertyAvail> results = client.searchAvail("downtown", SearchAvailPayload.builder()
    .segment("downtown")
    .roomType("suite")
    .dates(Arrays.asList("2026-07-20", "2026-07-21"))
    .limit(50L)
    .minPrice(100)
    .maxPrice(300)
    .amenities(Arrays.asList("wifi", "pool"))
    .rateFeature(Arrays.asList("free_cancellation"))
    .build());

for (PropertyAvail result : results) {
    System.out.println("Property: " + result.getPropertyId());
    for (DayAvail day : result.getDays()) {
        System.out.println("  " + day.getDate() + ": Avail " + day.getAvailability() + 
                          ", Price " + day.getFinalPrice());
    }
}
```

### getCodecs
Gets the current codec registry (used internally for validation).

```java
Codecs codecs = client.getCodecs();
System.out.println(codecs.getRateFeatures());
```

---

## Delete Operations

### delRoomDay
Deletes availability for a specific room on a specific date.

```java
client.delRoomDay("downtown", DelRoomDayRequest.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .date("2026-07-20")
    .build());
```

### delPropDay
Deletes all data for a property on a specific date.

```java
client.delPropDay("downtown", DelPropDayRequest.builder()
    .propertyId("hotel_123")
    .date("2026-07-20")
    .build());
```

### delPropRoom
Deletes a room type from a property.

```java
client.delPropRoom("downtown", DelPropRoomPayload.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .build());
```

### delProp
Deletes an entire property.

```java
client.delProp("downtown", "hotel_123");
```

### delSegment
Deletes a segment and all properties within it.

```java
client.delSegment("downtown");
```

---

## Error Handling

All methods throw `RoomzinException`. Use the helper methods to classify errors:

```java
import com.roomzin.roomzinjava.internal.protocol.RoomzinException;

try {
    client.setRoomPkg("downtown", payload);
} catch (RoomzinException e) {
    if (e.isRequest()) {
        // Business rule violation - fix the request
        System.out.println("Request error: " + e.getCode());
    } else if (e.isRetry()) {
        // Temporary condition - retry with backoff
        Thread.sleep(100);
        client.setRoomPkg("downtown", payload);
    } else if (e.isClient()) {
        // Authentication or protocol errors
        System.out.println("Client error: " + e.getMessage());
    } else if (e.isInternal()) {
        // Unexpected server response
        throw new RuntimeException("Internal error", e);
    } else {
        // Fatal error
        throw new RuntimeException("fatal", e);
    }
}
```

### Error Categories

| Category | Description | Action |
|----------|-------------|--------|
| **Client** | Authentication or protocol errors | Check credentials and configuration |
| **Request** | Invalid input or business rule violation | Fix request, don't retry |
| **Retry** | Temporary server condition (429, 503) | Retry with backoff |
| **Internal** | Unexpected server response | Log and investigate |

---

## Client Lifecycle

Create a **single client** during application startup and reuse it throughout your application.

```java
// ✅ Good - create once, reuse
RoomzinClient client = new RoomzinClient(config);
client.connect();
// Use client everywhere...
client.close();

// ❌ Bad - creating per request
for (Request req : requests) {
    RoomzinClient client = new RoomzinClient(config); // Don't do this
    client.setRoomPkg("downtown", req);
    client.close();
}
```

The client is safe for concurrent use and manages TCP connections internally.

---

## Architecture

### Standalone Mode

```
[SDK] → [Standalone Server]
```

- Single TCP connection
- Direct communication
- Self-healing on disconnection

### Cluster Mode

```
[SDK] → [Router] → [Shard Leader/Followers]
```

- SDK sends segment and isWrite flag in header
- Router routes writes to leader, reads to followers
- Router handles cluster topology
- SDK maintains single connection to router

### Protocol

The SDK uses a framed binary protocol:

**Standalone Frame:**
```
[0xFF][ClrID(4)][TotalLen(4)][Payload]
```

**Router Frame:**
```
[0xFE][TotalLen(4)][SegmentLen(1)][Segment(n)][IsWrite(1)][ShardFrame]
```

Where `ShardFrame` is the standalone frame format.

---

## Examples

A complete smoke example is available in the `examples/java/` directory. It demonstrates the SDK's core features and can be run as a reference implementation or to verify your Roomzin setup.

```bash
cd examples/java
mvn clean compile exec:java
```

---

## Documentation

For Roomzin concepts, deployment, and administration:

[https://m-javani.github.io/roomzin-doc/docs.html](https://m-javani.github.io/roomzin-doc/docs.html)

---

## Contributing

Contributions are welcome! Please open an issue before proposing large changes.

All contributions are subject to the BUSL-1.1 License terms.

---

## License

This SDK is licensed under the [BUSL-1.1 License](LICENSE).

**Note:** This SDK communicates with Roomzin Server, which requires a valid Roomzin license.

---

## Support

- **Documentation**: [roomzin-doc](https://m-javani.github.io/roomzin-doc/)
- **Community Q&A**: [GitHub Discussions](https://github.com/m-javani/roomzin-doc/discussions)
- **Issues**: [GitHub Issues](https://github.com/roomzin/roomzin-java/issues)
- **Security**: [mehdy.javany@gmail.com](mailto:mehdy.javany@gmail.com)

---

## Related Repositories

- [Roomzin Quickstart](https://github.com/m-javani/roomzin-quickstart) — Local Docker cluster
- [Roomzin Bench](https://github.com/m-javani/roomzin-bench) — Benchmarking tool