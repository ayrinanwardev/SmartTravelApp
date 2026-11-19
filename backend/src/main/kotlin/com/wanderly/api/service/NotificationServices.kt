package com.wanderly.api.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.*
import com.wanderly.api.config.WanderlyProperties
import com.wanderly.api.domain.DeviceToken
import com.wanderly.api.domain.Notification
import com.wanderly.api.domain.NotificationType
import com.wanderly.api.domain.User
import com.wanderly.api.dto.NotificationDto
import com.wanderly.api.exception.ApiException
import com.wanderly.api.realtime.afterCommit
import com.wanderly.api.realtime.RealtimePublisher
import com.wanderly.api.repository.DeviceTokenRepository
import com.wanderly.api.repository.NotificationRepository
import com.wanderly.api.repository.UserRepository
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.concurrent.Executors

/**
 * Notifications (trip created, booking paid, companion joined, budget alerts).
 * Each one is stored, pushed instantly to the user's open apps over the WebSocket
 * (`notification.created`), and sent as an FCM push when Firebase is configured.
 */
@Service
class NotificationService(
    private val repo: NotificationRepository,
    private val realtime: RealtimePublisher,
    private val push: PushService,
) {
    @Transactional
    fun notify(user: User, type: NotificationType, title: String, message: String) {
        if (!user.notificationsEnabled && type != NotificationType.SYSTEM) return
        val saved = repo.save(Notification(user, type, title, message))
        realtime.toUser(user.uid, "notification.created", NotificationDto.from(saved))
        push.sendAfterCommit(user.uid, title, message, mapOf("type" to type.json, "notificationId" to saved.id.toString()))
    }

    @Transactional(readOnly = true)
    fun list(user: User) = repo.findByUserIdOrderByCreatedAtDesc(user.uid).map(NotificationDto::from)

    @Transactional
    fun markRead(user: User, id: Long): NotificationDto {
        val n = repo.findByIdAndUserId(id, user.uid).orElseThrow { ApiException.notFound("Notification", id) }
        n.read = true
        return NotificationDto.from(n)
    }

    @Transactional
    fun markAllRead(user: User) = repo.findByUserIdOrderByCreatedAtDesc(user.uid).forEach { it.read = true }
}

/**
 * Sends push notifications through Firebase Cloud Messaging so users get them even when the
 * app is closed. A no-op unless `FIREBASE_CREDENTIALS_BASE64` holds a service-account JSON,
 * so the API runs fine without a Firebase project (in-app real-time still works).
 */
@Service
class PushService(
    props: WanderlyProperties,
    private val tokens: DeviceTokenRepository,
    private val users: UserRepository,
    txManager: PlatformTransactionManager,
) {
    companion object {
        const val ANDROID_CHANNEL = "wanderly_updates"
        private val log = LoggerFactory.getLogger(PushService::class.java)
        private val DEAD_TOKEN = setOf(
            MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT, MessagingErrorCode.SENDER_ID_MISMATCH,
        )
    }

    private val tx = TransactionTemplate(txManager)
    private val messaging: FirebaseMessaging? = if (props.push.enabled) init(props.push.firebaseCredentialsBase64) else null
    /** Sending is network I/O; never block the request thread on it. */
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    val enabled: Boolean get() = messaging != null

    init {
        log.info("Push notifications (FCM): {}", if (enabled) "enabled" else "disabled — set FIREBASE_CREDENTIALS_BASE64")
    }

    private fun init(base64: String): FirebaseMessaging? = try {
        val json = Base64.getDecoder().decode(base64.trim())
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(ByteArrayInputStream(json)))
            .build()
        val app = FirebaseApp.getApps().firstOrNull { it.name == "wanderly" } ?: FirebaseApp.initializeApp(options, "wanderly")
        FirebaseMessaging.getInstance(app)
    } catch (e: Exception) {
        log.warn("FCM disabled: could not load Firebase credentials ({})", e.message)
        null
    }

    @Transactional
    fun register(user: User, token: String, platform: String?) {
        val owner = users.getReferenceById(user.uid)
        val existing = tokens.findByToken(token).orElse(null)
        if (existing != null) existing.assignTo(owner, platform) else tokens.save(DeviceToken(owner, token, platform))
    }

    @Transactional
    fun unregister(user: User, token: String) {
        tokens.deleteByTokenAndUserId(token, user.uid)
    }

    /** Queues a push; inside a transaction it is sent only after commit. */
    fun sendAfterCommit(userId: Long, title: String, body: String, data: Map<String, String>) {
        if (messaging == null) return
        afterCommit { executor.submit { send(userId, title, body, data) } }
    }

    private fun send(userId: Long, title: String, body: String, data: Map<String, String>) {
        val fcm = messaging ?: return
        try {
            val targets = tx.execute { tokens.findTokens(userId) }.orEmpty()
            if (targets.isEmpty()) return
            val message = MulticastMessage.builder()
                .addAllTokens(targets)
                .setNotification(com.google.firebase.messaging.Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .setAndroidConfig(
                    AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder().setChannelId(ANDROID_CHANNEL).build())
                        .build()
                )
                .build()
            val result = fcm.sendEachForMulticast(message)
            val dead = result.responses.mapIndexedNotNull { i, r ->
                targets[i].takeIf { !r.isSuccessful && r.exception?.messagingErrorCode in DEAD_TOKEN }
            }
            if (dead.isNotEmpty()) {
                tx.executeWithoutResult { tokens.deleteByTokenIn(dead) }
                log.info("Removed {} expired FCM tokens", dead.size)
            }
        } catch (e: Exception) {
            log.warn("FCM send failed: {}", e.message)
        }
    }

    @PreDestroy
    fun shutdown() = executor.shutdown()
}
