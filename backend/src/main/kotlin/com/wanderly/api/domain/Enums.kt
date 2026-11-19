package com.wanderly.api.domain

import com.fasterxml.jackson.annotation.JsonValue

/*
 * Enums travel over JSON in lower case ("beach", "sightseeing", …) to match the Dart enum
 * names in the Flutter app. Jackson uses the @JsonValue property both ways.
 * They are stored in the database by name (EnumType.STRING).
 */

enum class DestinationCategory { BEACH, MOUNTAIN, CITY, ADVENTURE, CULTURE;

    @get:JsonValue val json: String get() = name.lowercase()
}

enum class Season { WINTER, SPRING, SUMMER, MONSOON, AUTUMN;

    @get:JsonValue val json: String get() = name.lowercase()
}

enum class ActivityType { SIGHTSEEING, FOOD, TRANSPORT, STAY, ADVENTURE, SHOPPING, RELAX;

    @get:JsonValue val json: String get() = name.lowercase()
}

enum class ExpenseCategory { FLIGHT, HOTEL, FOOD, SHOPPING, TRANSPORT, ACTIVITIES;

    @get:JsonValue val json: String get() = name.lowercase()
}

/** Reservation status (cancellation), independent of payment. */
enum class BookingStatus { CONFIRMED, CANCELLED;

    @get:JsonValue val json: String get() = name.lowercase()
}

enum class PaymentStatus { UNPAID, PAID, FAILED;

    @get:JsonValue val json: String get() = name.lowercase()
}

enum class NotificationType { TRIP, BOOKING, BUDGET, SYSTEM;

    @get:JsonValue val json: String get() = name.lowercase()
}

enum class Role { USER, ADMIN }
