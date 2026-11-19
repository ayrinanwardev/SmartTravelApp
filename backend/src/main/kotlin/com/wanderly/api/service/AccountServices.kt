package com.wanderly.api.service

import com.wanderly.api.domain.NotificationType
import com.wanderly.api.domain.User
import com.wanderly.api.dto.*
import com.wanderly.api.exception.ApiException
import com.wanderly.api.repository.*
import com.wanderly.api.security.JwtService
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val users: UserRepository,
    private val encoder: PasswordEncoder,
    private val jwt: JwtService,
    private val notifications: NotificationService,
) {
    @Transactional
    fun register(req: RegisterRequest): AuthResponse {
        val email = req.email.trim().lowercase()
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("An account with this email already exists")
        val user = users.save(User(req.name.trim(), email, encoder.encode(req.password)!!))
        notifications.notify(
            user, NotificationType.SYSTEM, "Welcome to Wanderly, ${user.name.substringBefore(' ')}!",
            "Start by exploring destinations or ask the trip assistant to plan your first trip.",
        )
        return toResponse(user)
    }

    @Transactional(readOnly = true)
    fun login(req: LoginRequest): AuthResponse {
        val user = users.findByEmailIgnoreCase(req.email.trim()).orElse(null)
            ?.takeIf { encoder.matches(req.password, it.password) }
            ?: throw BadCredentialsException("bad credentials")
        return toResponse(user)
    }

    private fun toResponse(user: User) = AuthResponse(jwt.issue(user), "Bearer", jwt.ttlSeconds, UserDto.from(user))
}

@Service
class UserService(
    private val users: UserRepository,
    private val trips: TripRepository,
    private val wishlist: WishlistRepository,
    private val journal: JournalRepository,
    private val bookings: BookingRepository,
    private val notifications: NotificationRepository,
) {
    /** A fresh, managed copy of the authenticated user (the principal is detached). */
    @Transactional(readOnly = true)
    fun load(principal: User): User =
        users.findById(principal.uid).orElseThrow { ApiException.notFound("User", principal.id) }

    @Transactional(readOnly = true)
    fun me(principal: User) = UserDto.from(load(principal))

    @Transactional
    fun update(principal: User, req: UpdateProfileRequest): UserDto {
        val u = load(principal)
        req.name?.takeIf { it.isNotBlank() }?.let { u.name = it.trim() }
        req.notificationsEnabled?.let { u.notificationsEnabled = it }
        return UserDto.from(users.save(u))
    }

    @Transactional(readOnly = true)
    fun stats(principal: User): ProfileStats {
        val id = principal.uid
        return ProfileStats(
            trips.findAccessible(id).size.toLong(), wishlist.countByUserId(id), journal.countByUserId(id),
            bookings.countByUserId(id), notifications.countByUserIdAndReadFalse(id),
        )
    }
}
