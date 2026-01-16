package com.kuklin.manageapp.bots.caloriebot.models;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;

import java.util.List;

public record NutritionAnalysisPayloadRecord(List<Dish> dishes,
                                             UserNutritionProfile userProfile,
                                             String userSettingsTimezoneId) {
}
