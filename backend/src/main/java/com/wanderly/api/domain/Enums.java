package com.wanderly.api.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enums are sent over JSON in lower case ("beach", "sightseeing", ...) to
 * match the Dart enum names used by the Flutter app. Jackson uses the
 * {@link JsonValue} method for both serialising and deserialising.
 */
public final class Enums {

    private Enums() {
    }

    public enum DestinationCategory {
        BEACH, MOUNTAIN, CITY, ADVENTURE, CULTURE;

        @JsonValue
        public String json() {
            return name().toLowerCase();
        }
    }

    public enum Season {
        WINTER, SPRING, SUMMER, MONSOON, AUTUMN;

        @JsonValue
        public String json() {
            return name().toLowerCase();
        }
    }

    public enum ActivityType {
        SIGHTSEEING, FOOD, TRANSPORT, STAY, ADVENTURE, SHOPPING, RELAX;

        @JsonValue
        public String json() {
            return name().toLowerCase();
        }
    }

    public enum ExpenseCategory {
        FLIGHT, HOTEL, FOOD, SHOPPING, TRANSPORT, ACTIVITIES;

        @JsonValue
        public String json() {
            return name().toLowerCase();
        }
    }

    public enum BookingStatus {
        CONFIRMED, CANCELLED;

        @JsonValue
        public String json() {
            return name().toLowerCase();
        }
    }

    public enum NotificationType {
        TRIP, BOOKING, BUDGET, SYSTEM;

        @JsonValue
        public String json() {
            return name().toLowerCase();
        }
    }

    public enum Role {
        USER, ADMIN
    }
}
