package com.wanderly.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Typical per-person costs in BDT. */
@Embeddable
public class CostEstimate {

    @Column(name = "cost_transport")
    private int transport;

    @Column(name = "cost_hotel_per_night")
    private int hotelPerNight;

    @Column(name = "cost_food_per_day")
    private int foodPerDay;

    @Column(name = "cost_activities_per_day")
    private int activitiesPerDay;

    public CostEstimate() {
    }

    public CostEstimate(int transport, int hotelPerNight, int foodPerDay, int activitiesPerDay) {
        this.transport = transport;
        this.hotelPerNight = hotelPerNight;
        this.foodPerDay = foodPerDay;
        this.activitiesPerDay = activitiesPerDay;
    }

    /** Same formula as the Flutter app: return transport + nights of hotel + daily spend. */
    public int forDays(int days, int travelers) {
        int nights = Math.max(1, days - 1);
        return transport * travelers + hotelPerNight * nights + (foodPerDay + activitiesPerDay) * days * travelers;
    }

    public int getTransport() { return transport; }
    public int getHotelPerNight() { return hotelPerNight; }
    public int getFoodPerDay() { return foodPerDay; }
    public int getActivitiesPerDay() { return activitiesPerDay; }
}
