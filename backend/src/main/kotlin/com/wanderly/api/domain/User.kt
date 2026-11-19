package com.wanderly.api.domain

import jakarta.persistence.*
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.time.Instant

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false, unique = true, length = 160)
    var email: String,

    @Column(name = "password_hash", nullable = false)
    private var passwordHash: String,
) : UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: Role = Role.USER

    /** Overall travel budget shown on the Budget screen when no trip is selected. */
    @Column(name = "budget_total", nullable = false)
    var budgetTotal: Int = 40_000

    @Column(name = "notifications_enabled", nullable = false)
    var notificationsEnabled: Boolean = true

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    /** Non-null id of a persisted user. */
    val uid: Long get() = requireNotNull(id) { "User is not persisted yet" }

    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
    override fun getPassword(): String = passwordHash
    override fun getUsername(): String = email
}
