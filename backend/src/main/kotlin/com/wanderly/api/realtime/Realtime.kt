package com.wanderly.api.realtime

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.wanderly.api.dto.LocationDto
import com.wanderly.api.repository.TripMemberRepository
import com.wanderly.api.repository.TripRepository
import com.wanderly.api.repository.UserRepository
import com.wanderly.api.security.JwtService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Runs [block] after the current transaction commits, or immediately when there is none. */
fun afterCommit(block: () -> Unit) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = block()
        })
    } else {
        block()
    }
}

/**
 * Keeps every open WebSocket per user and pushes JSON events to them:
 * `{"type": "notification.created", "data": {...}}`.
 *
 * State is in memory, which is right for a single instance (Render free plan). To run several
 * instances, fan events out through Redis pub/sub or Postgres LISTEN/NOTIFY.
 */
@Component
class RealtimeHub(private val json: JsonMapper) {

    companion object {
        private val log = LoggerFactory.getLogger(RealtimeHub::class.java)
        /** Shared positions older than this are not replayed to new viewers. */
        val LOCATION_TTL: Duration = Duration.ofMinutes(10)
    }

    private val sessionsByUser = ConcurrentHashMap<Long, MutableSet<WebSocketSession>>()
    /** tripId → (userId → last shared position). */
    private val locations = ConcurrentHashMap<String, ConcurrentHashMap<Long, LocationDto>>()

    fun register(userId: Long, session: WebSocketSession) {
        sessionsByUser.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    /** Returns true if this was the user's last open connection. */
    fun unregister(userId: Long, session: WebSocketSession): Boolean {
        val set = sessionsByUser[userId] ?: return true
        set.remove(session)
        if (set.isEmpty()) {
            sessionsByUser.remove(userId, set)
            return true
        }
        return false
    }

    fun isOnline(userId: Long) = sessionsByUser[userId]?.isNotEmpty() == true

    fun connectionCount() = sessionsByUser.values.sumOf { it.size }

    fun send(userId: Long, type: String, data: Any?) {
        val sessions = sessionsByUser[userId]?.takeIf { it.isNotEmpty() } ?: return
        val payload = encode(type, data)
        sessions.forEach { sendRaw(it, payload) }
    }

    fun send(userIds: Collection<Long>, type: String, data: Any?) = userIds.toSet().forEach { send(it, type, data) }

    fun sendRaw(session: WebSocketSession, payload: String) {
        if (!session.isOpen) return
        try {
            session.sendMessage(TextMessage(payload))
        } catch (e: IOException) {
            log.debug("websocket send failed: {}", e.message)
        } catch (e: IllegalStateException) {
            log.debug("websocket send failed: {}", e.message)
        }
    }

    fun encode(type: String, data: Any?): String = json.writeValueAsString(mapOf("type" to type, "data" to data))

    // ---- live locations ----

    fun putLocation(loc: LocationDto) {
        locations.computeIfAbsent(loc.tripId) { ConcurrentHashMap() }[loc.userId] = loc
    }

    /** Removes the user's position from every trip and returns those trip ids. */
    fun removeLocations(userId: Long): List<String> =
        locations.entries.mapNotNull { (tripId, byUser) -> tripId.takeIf { byUser.remove(userId) != null } }

    fun removeLocation(tripId: String, userId: Long) = locations[tripId]?.remove(userId) != null

    fun currentLocations(tripId: String): List<LocationDto> {
        val cutoff = Instant.now().minus(LOCATION_TTL)
        return locations[tripId]?.values?.filter { it.at.isAfter(cutoff) }.orEmpty()
    }
}

/**
 * Publishes real-time events from services. Inside a transaction the event is sent only
 * after a successful commit, so clients never react to data that was rolled back.
 */
@Component
class RealtimePublisher(private val hub: RealtimeHub) {
    fun toUser(userId: Long, type: String, data: Any?) = toUsers(listOf(userId), type, data)

    fun toUsers(userIds: Collection<Long>, type: String, data: Any?) {
        val targets = userIds.toList()
        afterCommit { hub.send(targets, type, data) }
    }
}

/**
 * Who belongs to a trip (owner + companions). Cached briefly because live location updates
 * arrive every few seconds; membership changes call [evict].
 */
@Component
class TripAccess(private val trips: TripRepository, private val members: TripMemberRepository) {

    private data class Entry(val userIds: Set<Long>, val loadedAt: Instant)

    private val ttl: Duration = Duration.ofSeconds(30)
    private val cache = ConcurrentHashMap<String, Entry>()

    /** Owner first, then companions. Empty if the trip does not exist. */
    @Transactional(readOnly = true)
    fun participants(tripId: String): Set<Long> {
        cache[tripId]?.takeIf { it.loadedAt.plus(ttl).isAfter(Instant.now()) }?.let { return it.userIds }
        val ids = linkedSetOf<Long>()
        trips.findById(tripId).ifPresent { t ->
            // `id` (not `uid`) so Hibernate can answer from the proxy without loading the user.
            ids += t.user.id!!
            ids += members.findUserIds(tripId)
        }
        return ids.also { cache[tripId] = Entry(it, Instant.now()) }
    }

    // Transactional too: it is called from WebSocket threads and calls participants() directly (no proxy).
    @Transactional(readOnly = true)
    fun canAccess(tripId: String?, userId: Long) = tripId != null && userId in participants(tripId)

    fun evict(tripId: String) {
        cache.remove(tripId)
    }
}

/**
 * Authenticates the WebSocket upgrade with the same JWT as the REST API, sent as
 * `Authorization: Bearer …` or `?token=…` (browsers can't set headers on WebSockets).
 * Invalid or missing tokens get HTTP 401 and no connection.
 */
@Component
class JwtHandshakeInterceptor(private val jwt: JwtService, private val users: UserRepository) : HandshakeInterceptor {

    companion object {
        const val USER_ID = "userId"
        const val USER_NAME = "userName"
    }

    override fun beforeHandshake(
        request: ServerHttpRequest, response: ServerHttpResponse, wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val header = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        val token = header?.takeIf { it.startsWith("Bearer ") }?.substring(7)
            ?: UriComponentsBuilder.fromUri(request.uri).build().queryParams.getFirst("token")
        val user = token?.let { jwt.validate(it) }?.let { users.findByEmailIgnoreCase(it).orElse(null) }
        if (user == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }
        attributes[USER_ID] = user.uid
        attributes[USER_NAME] = user.name
        return true
    }

    override fun afterHandshake(request: ServerHttpRequest, response: ServerHttpResponse, wsHandler: WebSocketHandler, exception: Exception?) {}
}

/**
 * WebSocket endpoint `/ws?token=<JWT>`.
 *
 * ```
 * client → server
 *   {"type":"ping"}
 *   {"type":"trip.watch",      "tripId":"…"}                        → location.snapshot
 *   {"type":"location.update", "tripId":"…","lat":…,"lng":…,"accuracy":…}
 *   {"type":"location.stop",   "tripId":"…"}
 * server → client
 *   connected · pong · notification.created · booking.updated · trip.updated · trip.deleted
 *   trip.member_joined · location.snapshot · location.updated · location.stopped · error
 * ```
 */
@Component
class RealtimeWebSocketHandler(
    private val hub: RealtimeHub,
    private val access: TripAccess,
    private val json: JsonMapper,
) : TextWebSocketHandler() {

    companion object {
        private val log = LoggerFactory.getLogger(RealtimeWebSocketHandler::class.java)
        private val MIN_UPDATE_INTERVAL: Duration = Duration.ofMillis(900)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ClientMessage(
        val type: String? = null, val tripId: String? = null,
        val lat: Double? = null, val lng: Double? = null, val accuracy: Double? = null,
    )

    /** Thread-safe wrapper per raw session id (services and other users write concurrently). */
    private val safe = ConcurrentHashMap<String, WebSocketSession>()
    private val lastUpdate = ConcurrentHashMap<String, Instant>()

    private val WebSocketSession.userId get() = attributes[JwtHandshakeInterceptor.USER_ID] as Long
    private val WebSocketSession.userName get() = attributes[JwtHandshakeInterceptor.USER_NAME] as String

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val wrapped = ConcurrentWebSocketSessionDecorator(session, 5_000, 256 * 1024)
        safe[session.id] = wrapped
        hub.register(session.userId, wrapped)
        hub.sendRaw(wrapped, hub.encode("connected", mapOf("userId" to session.userId)))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val out = safe[session.id] ?: session
        val msg = try {
            json.readValue(message.payload, ClientMessage::class.java)
        } catch (e: RuntimeException) {
            return error(out, "Invalid JSON")
        }
        val uid = session.userId
        when (msg.type) {
            "ping" -> hub.sendRaw(out, hub.encode("pong", mapOf("at" to Instant.now().toString())))
            "trip.watch" -> {
                if (!access.canAccess(msg.tripId, uid)) return error(out, "Not a member of this trip")
                hub.sendRaw(out, hub.encode("location.snapshot", mapOf("tripId" to msg.tripId, "locations" to hub.currentLocations(msg.tripId!!))))
            }
            "location.update" -> onLocation(session, out, uid, msg)
            "location.stop" -> {
                val tripId = msg.tripId
                if (tripId != null && access.canAccess(tripId, uid) && hub.removeLocation(tripId, uid)) {
                    hub.send(others(tripId, uid), "location.stopped", mapOf("tripId" to tripId, "userId" to uid))
                }
            }
            else -> error(out, "Unknown type: ${msg.type}")
        }
    }

    private fun onLocation(session: WebSocketSession, out: WebSocketSession, uid: Long, msg: ClientMessage) {
        val lat = msg.lat
        val lng = msg.lng
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) return error(out, "lat/lng out of range")
        val tripId = msg.tripId
        if (tripId == null || !access.canAccess(tripId, uid)) return error(out, "Not a member of this trip")
        // Per-connection rate limit so a buggy client can't flood the trip.
        val now = Instant.now()
        val prev = lastUpdate.put(session.id, now)
        if (prev != null && Duration.between(prev, now) < MIN_UPDATE_INTERVAL) return

        val loc = LocationDto(tripId, uid, session.userName, lat, lng, msg.accuracy, now)
        hub.putLocation(loc)
        hub.send(others(tripId, uid), "location.updated", loc)
    }

    private fun others(tripId: String, uid: Long) = access.participants(tripId) - uid

    private fun error(out: WebSocketSession, message: String) = hub.sendRaw(out, hub.encode("error", mapOf("message" to message)))

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val wrapped = safe.remove(session.id)
        lastUpdate.remove(session.id)
        val uid = session.userId
        if (hub.unregister(uid, wrapped ?: session)) {
            // User went offline: stop showing them on companions' maps.
            hub.removeLocations(uid).forEach { tripId ->
                hub.send(others(tripId, uid), "location.stopped", mapOf("tripId" to tripId, "userId" to uid))
            }
        }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        log.debug("websocket transport error: {}", exception.message)
    }
}

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val handler: RealtimeWebSocketHandler,
    private val auth: JwtHandshakeInterceptor,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/ws")
            .addInterceptors(auth)
            // Mobile apps send no Origin; authentication is the JWT, not the origin.
            .setAllowedOriginPatterns("*")
    }
}
