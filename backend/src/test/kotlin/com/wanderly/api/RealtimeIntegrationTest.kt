package com.wanderly.api

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/**
 * Real WebSocket clients against the running server: JWT handshake, live companion
 * location sharing, live itinerary sync and instant notifications.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Import(TestPaymentConfig::class)
@TestPropertySource(properties = ["wanderly.uploads.dir=target/test-uploads", "spring.datasource.url=jdbc:h2:mem:realtime-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"])
class RealtimeIntegrationTest {

    @Value("\${local.server.port}") var port: Int = 0

    private val http: HttpClient = HttpClient.newHttpClient()
    private val sockets = mutableListOf<WebSocket>()

    @AfterEach
    fun close() = sockets.forEach { it.abort() }

    /**
     * Collects every text frame. [await] takes the first not-yet-consumed frame of a type and
     * leaves the others, so events that arrive in a different order than awaited aren't lost.
     */
    private class Client : WebSocket.Listener {
        private val frames = java.util.Collections.synchronizedList(mutableListOf<String>())
        private val buffer = StringBuilder()

        override fun onText(ws: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                frames += buffer.toString()
                buffer.setLength(0)
            }
            ws.request(1)
            return null
        }

        fun await(type: String, timeoutSeconds: Long = 5): String {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            while (System.nanoTime() < deadline) {
                synchronized(frames) {
                    val i = frames.indexOfFirst { JsonPath.read<String>(it, "$.type") == type }
                    if (i >= 0) return frames.removeAt(i)
                }
                Thread.sleep(50)
            }
            throw AssertionError("No '$type' event within ${timeoutSeconds}s; received ${frames.map { JsonPath.read<String>(it, "$.type") }}")
        }
    }

    private fun call(method: String, path: String, token: String? = null, json: String? = null): String {
        val b = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .method(method, json?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
            .header("Content-Type", "application/json")
        token?.let { b.header("Authorization", "Bearer $it") }
        val res = http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        assertThat(res.statusCode()).describedAs("$method $path → ${res.body()}").isLessThan(300)
        return res.body()
    }

    private fun register(prefix: String): String {
        val email = "$prefix-${UUID.randomUUID().toString().take(8)}@example.com"
        return JsonPath.read(call("POST", "/api/auth/register", json = """{"name":"$prefix","email":"$email","password":"secret123"}"""), "$.token")
    }

    private fun connect(token: String?): Pair<WebSocket, Client> {
        val client = Client()
        val q = token?.let { "?token=$it" }.orEmpty()
        val ws = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
            .buildAsync(URI("ws://localhost:$port/ws$q"), client).get(5, TimeUnit.SECONDS)
        sockets += ws
        return ws to client
    }

    @Test
    fun `handshake without a valid token is refused`() {
        val failed = CompletableFuture.supplyAsync { runCatching { connect("bogus") }.isFailure }.get(10, TimeUnit.SECONDS)
        assertThat(failed).isTrue()
    }

    @Test
    fun `companions see each other's live location and itinerary changes`() {
        val owner = register("Owner")
        val friend = register("Friend")
        val start = LocalDate.now().plusDays(7)
        val tripId: String = JsonPath.read(
            call("POST", "/api/trips", owner, """{"destinationId":"sajek","startDate":"$start","endDate":"${start.plusDays(1)}","travelers":2}"""),
            "$.id",
        )
        val code: String = JsonPath.read(call("POST", "/api/trips/$tripId/share", owner), "$.code")

        val (ownerWs, ownerEvents) = connect(owner)
        ownerEvents.await("connected")
        call("POST", "/api/trips/join", friend, """{"code":"$code"}""")
        // Owner is told instantly that a companion joined (event + notification)
        assertThat(ownerEvents.await("trip.member_joined")).contains("\"name\":\"Friend\"")
        assertThat(ownerEvents.await("notification.created")).contains("joined")

        val (friendWs, friendEvents) = connect(friend)
        friendEvents.await("connected")

        // Friend shares location → owner receives it
        friendWs.sendText("""{"type":"location.update","tripId":"$tripId","lat":23.3815,"lng":92.2936,"accuracy":8}""", true).join()
        val rejected = runCatching { friendEvents.await("error", 1) }.getOrNull()
        assertThat(rejected).describedAs("server rejected the location update").isNull()
        val loc = ownerEvents.await("location.updated")
        assertThat(JsonPath.read<Double>(loc, "$.data.lat")).isEqualTo(23.3815)
        assertThat(JsonPath.read<String>(loc, "$.data.name")).isEqualTo("Friend")

        // Owner opening the live map gets the latest positions as a snapshot
        ownerWs.sendText("""{"type":"trip.watch","tripId":"$tripId"}""", true).join()
        assertThat(ownerEvents.await("location.snapshot")).contains("23.3815")

        // Outsiders can't watch or post locations
        val (strangerWs, strangerEvents) = connect(register("Stranger"))
        strangerEvents.await("connected")
        strangerWs.sendText("""{"type":"trip.watch","tripId":"$tripId"}""", true).join()
        assertThat(strangerEvents.await("error")).contains("Not a member")

        // Owner edits the itinerary → friend is told to refresh
        call("POST", "/api/trips/$tripId/days/1/activities", owner, """{"title":"Sunrise at Konglak","time":"05:30","type":"sightseeing"}""")
        assertThat(friendEvents.await("trip.updated")).contains(tripId)

        // Friend stops sharing → owner removes the marker
        friendWs.sendText("""{"type":"location.stop","tripId":"$tripId"}""", true).join()
        assertThat(ownerEvents.await("location.stopped")).contains(tripId)

        // Ping / pong keep-alive
        ownerWs.sendText("""{"type":"ping"}""", true).join()
        ownerEvents.await("pong")
    }
}
