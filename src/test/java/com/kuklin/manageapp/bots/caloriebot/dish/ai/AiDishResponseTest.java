package com.kuklin.manageapp.bots.caloriebot.dish.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.caloriebot.dish.Dish;
import com.kuklin.manageapp.bots.caloriebot.dish.dto.DishDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Схема ответа подходит под strict-режим OpenAI, а итог на порцию считается из значений на 100 г.
 */
class AiDishResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode getDishSchema() {
        JsonNode schema = mapper.valueToTree(AiDishResponse.RESPONSE_FORMAT.jsonSchema().schema());
        return schema.path("properties").path("dishes").path("items");
    }

    /** strict: все поля в required, лишние запрещены — иначе OpenAI отклонит схему. */
    @Test
    void schemaIsStrictCompatible() {
        JsonNode root = mapper.valueToTree(AiDishResponse.RESPONSE_FORMAT.jsonSchema().schema());
        for (JsonNode object : List.of(root, getDishSchema())) {
            List<String> properties = new ArrayList<>();
            object.path("properties").fieldNames().forEachRemaining(properties::add);
            List<String> required = new ArrayList<>();
            object.path("required").forEach(n -> required.add(n.asText()));

            assertThat(required).containsExactlyInAnyOrderElementsOf(properties);
            assertThat(object.path("additionalProperties").asBoolean(true)).isFalse();
        }
        assertThat(AiDishResponse.RESPONSE_FORMAT.jsonSchema().strict()).isTrue();
    }

    /** Модель пишет поля по порядку: сначала рассуждение, потом вес, потом пищевая ценность. */
    @Test
    void estimationAndWeightGoBeforeNutrition() {
        List<String> order = new ArrayList<>();
        getDishSchema().path("properties").fieldNames().forEachRemaining(order::add);

        assertThat(order.get(0)).isEqualTo("estimation");
        assertThat(order.indexOf("weightGrams")).isLessThan(order.indexOf("caloriesPer100g"));
    }

    @Test
    void categoryEnumMatchesFoodCategory() {
        List<String> values = new ArrayList<>();
        getDishSchema().path("properties").path("category").path("enum").forEach(n -> values.add(n.asText()));

        assertThat(values).containsExactlyElementsOf(
                Arrays.stream(Dish.FoodCategory.values()).map(Enum::name).toList());
    }

    @Test
    void totalsAreComputedFromPer100gAndWeight() {
        DishDto dto = new AiDishResponse.AiDish("3 штуки по ~12 г", "Печенье", "🍪", Dish.FoodCategory.DESSERT,
                36, 3, 450.0, 6.0, 20.0, 65.0, 75).toDishDto();

        assertThat(dto.getWeight()).isEqualTo(36);
        assertThat(dto.getPortions()).isEqualTo(3);
        assertThat(dto.getPortionWeight()).isEqualTo(12);
        assertThat(dto.getCalories()).isEqualTo(162);
        assertThat(dto.getProteins()).isEqualTo(2);
        assertThat(dto.getFats()).isEqualTo(7);
        assertThat(dto.getCarbohydrates()).isEqualTo(23);
        assertThat(dto.getIsDish()).isTrue();
    }

    @Test
    void missingValuesFallBackToSafeDefaults() {
        DishDto dto = new AiDishResponse.AiDish(null, "Чай", null, null,
                null, 0, null, -1.0, null, null, null).toDishDto();

        assertThat(dto.getWeight()).isEqualTo(1);
        assertThat(dto.getPortions()).isEqualTo(1);
        assertThat(dto.getPortionWeight()).isEqualTo(1);
        assertThat(dto.getCalories()).isZero();
        assertThat(dto.getProteins()).isZero();
        assertThat(dto.getCategory()).isEqualTo(Dish.FoodCategory.UNKNOWN);
    }
}
