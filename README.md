# Roomzin Java SDK

Official Java SDK for [Roomzin](https://m-javani.github.io/roomzin-doc/) — a high-performance in-memory inventory engine for booking platforms.

The SDK provides a clean, idiomatic Java interface for communicating with Roomzin servers in both standalone and clustered deployments. It automatically manages routing, failover, connection pooling, and cluster topology changes.

---

## Features

- Automatic request routing (leader for writes, followers for reads)
- Built-in failover and cluster discovery
- Connection pooling
- Standalone and clustered deployment support
- Fully typed Java API
- AutoCloseable client for resource management

---

## Requirements

- Java 8 or later
- Roomzin Server v1.x

---

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.m-javani</groupId>
    <artifactId>roomzin-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
implementation 'com.roomzin:roomzin-java:1.0.0'
```

---

## Client Setup

### Standalone

```java
import com.roomzin.roomzinjava.single.SingleClient;
import com.roomzin.roomzinjava.api.CacheClientApi;
import java.time.Duration;

CacheClientApi client = SingleConfig.builder()
    .withHost("127.0.0.1")
    .withTcpPort(7777)
    .withAuthToken("abc123")
    .withTimeout(Duration.ofSeconds(5))
    .withKeepAlive(Duration.ofSeconds(30))
    .build();

// Use client...
client.close();
```

### Cluster (Static Discovery)

```java
import com.roomzin.roomzinjava.cluster.ClusterClient;
import com.roomzin.roomzinjava.cluster.ClusterConfig;
import com.roomzin.roomzinjava.types.NodeAddr;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

List<NodeAddr> staticDiscovery = Arrays.asList(
    new NodeAddr("roomzin-0", "172.20.0.10", 7777, 8080),
    new NodeAddr("roomzin-1", "172.20.0.11", 7777, 8080),
    new NodeAddr("roomzin-2", "172.20.0.12", 7777, 8080)
);

ClusterConfig cfg = ClusterConfig.builder()
    .withSeedNodeIds("roomzin-0, roomzin-1, roomzin-2")
    .withStaticDiscovery(staticDiscovery)
    .withTcpPort(7777)
    .withApiPort(8080)
    .withAuthToken("abc123")
    .withTimeout(Duration.ofSeconds(5))
    .withKeepAlive(Duration.ofSeconds(30))
    .build();

CacheClientApi client = ClusterClient.create(cfg);
client.close();
```

### Cluster (HTTP Discovery)

```java
ClusterConfig cfg = ClusterConfig.builder()
    .withSeedNodeIds("roomzin-0, roomzin-1, roomzin-2")
    .withHttpDiscovery("http://discovery-service:8080/nodes")
    .withTcpPort(7777)
    .withApiPort(8080)
    .withAuthToken("abc123")
    .withTimeout(Duration.ofSeconds(5))
    .withKeepAlive(Duration.ofSeconds(30))
    .build();

CacheClientApi client = ClusterClient.create(cfg);
```

---

## Discovery Configuration

Roomzin SDKs need to know how to reach each Roomzin node in the cluster. The cluster nodes communicate with each other using internal address resolvers, but the SDK as an external client needs actual network addresses (IP:port or hostname:port) to connect.

The SDK fetches the cluster topology from the Roomzin cluster itself. This topology includes the node identities of the leader and followers. The SDK then uses discovery to resolve these node identities into actual network addresses.

Two discovery modes are supported:

### Static Discovery

The SDK gets the mapping once in config and never updates it. Use this when your cluster nodes have stable, predictable addresses.

### HTTP Discovery

The SDK periodically fetches the mapping from an HTTP endpoint. Use this when cluster nodes are dynamic (e.g., Kubernetes pods with changing IPs).

---

## Property Management

### setProp
Adds or updates a property.

```java
client.setProp(SetPropPayload.builder()
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
List<String> ids = client.searchProp(SearchPropPayload.builder()
    .segment("downtown")
    .build());

// By area
List<String> ids = client.searchProp(SearchPropPayload.builder()
    .segment("downtown")
    .area("manhattan")
    .build());

// By location (radius search)
List<String> ids = client.searchProp(SearchPropPayload.builder()
    .segment("downtown")
    .latitude(40.7128)
    .longitude(-74.0060)
    .build());
```

### propExist
Checks if a property exists.

```java
boolean exists = client.propExist("hotel_123");
```

### propRoomExist
Checks if a specific room type exists for a property.

```java
boolean exists = client.propRoomExist(PropRoomExistPayload.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .build());
```

### propRoomList
Lists all room types for a property.

```java
List<String> rooms = client.propRoomList("hotel_123");
```

### propRoomDateList
Lists dates with availability data for a property and room type.

```java
List<String> dates = client.propRoomDateList(PropRoomDateListPayload.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .build());
```

---

## Room Package Management

### setRoomPkg
Sets availability, price, and rate features for a room type on a date.

```java
client.setRoomPkg(SetRoomPkgPayload.builder()
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
short newAvail = client.setRoomAvl(UpdRoomAvlPayload.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .date("2026-07-20")
    .amount((short) 20)
    .build());
```

### incRoomAvl
Increases availability (e.g., on cancellation).

```java
short newAvail = client.incRoomAvl(UpdRoomAvlPayload.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .date("2026-07-20")
    .amount((short) 1)
    .build());
```

### decRoomAvl
Decreases availability (e.g., on booking).

```java
short newAvail = client.decRoomAvl(UpdRoomAvlPayload.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .date("2026-07-20")
    .amount((short) 2)
    .build());
```

### getPropRoomDay
Gets availability and pricing for a specific room on a specific date.

```java
GetRoomDayResult day = client.getPropRoomDay(GetRoomDayRequest.builder()
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
List<PropertyAvail> results = client.searchAvail(SearchAvailPayload.builder()
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

### getSegments
Lists all active segments with their property counts.

```java
List<SegmentInfo> segments = client.getSegments();
for (SegmentInfo seg : segments) {
    System.out.println(seg.getSegment() + ": " + seg.getCount() + " properties");
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
client.delRoomDay(DelRoomDayRequest.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .date("2026-07-20")
    .build());
```

### delPropDay
Deletes all data for a property on a specific date.

```java
client.delPropDay(DelPropDayRequest.builder()
    .propertyId("hotel_123")
    .date("2026-07-20")
    .build());
```

### delPropRoom
Deletes a room type from a property.

```java
client.delPropRoom(DelPropRoomPayload.builder()
    .propertyId("hotel_123")
    .roomType("suite")
    .build());
```

### delProp
Deletes an entire property.

```java
client.delProp("hotel_123");
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
    client.setRoomPkg(payload);
} catch (RoomzinException e) {
    if (e.isRequest()) {
        // Business rule violation - fix the request
        System.out.println("Request error: " + e.getCode());
    } else if (e.isRetry()) {
        // Temporary condition - retry with backoff
        Thread.sleep(100);
        client.setRoomPkg(payload);
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
| **Retry** | Temporary server condition (429, 503, 308) | Retry with backoff |
| **Internal** | Unexpected server response | Log and investigate |

---

## Client Lifecycle

Create a **single client** during application startup and reuse it throughout your application.

```java
// ✅ Good - create once, reuse
CacheClientApi client = SingleConfig.builder()
    .withHost("127.0.0.1")
    .withTcpPort(7777)
    .withAuthToken("abc123")
    .build();
// Use client everywhere...
client.close();

// ❌ Bad - creating per request
for (Request req : requests) {
    CacheClientApi client = SingleConfig.builder() // Don't do this
        .withHost("127.0.0.1")
        .withTcpPort(7777)
        .build();
    client.setRoomPkg(req);
    client.close();
}
```

The client is safe for concurrent use and manages TCP connections internally.

---

## API Reference

For the complete interface definition, see [`CacheClientApi.java`](src/main/java/com/roomzin/roomzinjava/api/CacheClientApi.java). All types are documented with Javadoc comments.

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

---
