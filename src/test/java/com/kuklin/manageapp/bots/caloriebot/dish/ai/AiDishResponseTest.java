package com.kuklin.manageapp.bots.caloriebot.dish.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.caloriebot.dish.Dish;
import com.kuklin.manageapp.bots.caloriebot.dish.Micronutrient;
import com.kuklin.manageapp.bots.caloriebot.dish.dto.DishDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
                36, 3, 450.0, 6.0, 20.0, 65.0, 2.0, null, 75).toDishDto();

        assertThat(dto.getWeight()).isEqualTo(36);
        assertThat(dto.getPortions()).isEqualTo(3);
        assertThat(dto.getPortionWeight()).isEqualTo(12);
        assertThat(dto.getCalories()).isEqualTo(162);
        assertThat(dto.getProteins()).isEqualTo(2);
        assertThat(dto.getFats()).isEqualTo(7);
        assertThat(dto.getCarbohydrates()).isEqualTo(23);
        assertThat(dto.getFiber()).isEqualTo(1);
        assertThat(dto.getMicronutrients()).isNull();
        assertThat(dto.getIsDish()).isTrue();
    }

    @Test
    void missingValuesFallBackToSafeDefaults() {
        DishDto dto = new AiDishResponse.AiDish(null, "Чай", null, null,
                null, 0, null, -1.0, null, null, null, null, null).toDishDto();

        assertThat(dto.getWeight()).isEqualTo(1);
        assertThat(dto.getPortions()).isEqualTo(1);
        assertThat(dto.getPortionWeight()).isEqualTo(1);
        assertThat(dto.getCalories()).isZero();
        assertThat(dto.getProteins()).isZero();
        assertThat(dto.getCategory()).isEqualTo(Dish.FoodCategory.UNKNOWN);
        assertThat(dto.getFiber()).isNull();
    }

    /** Premium-схема: то же плюс micronutrientsPer100g со всеми ключами Micronutrient, тоже strict, перед aiConfidence. */
    @Test
    void premiumSchemaAddsMicronutrients() {
        JsonNode root = mapper.valueToTree(AiDishResponse.RESPONSE_FORMAT_PREMIUM.jsonSchema().schema());
        JsonNode item = root.path("properties").path("dishes").path("items");
        JsonNode micronutrients = item.path("properties").path(AiDishResponse.MICRONUTRIENTS_FIELD);
        for (JsonNode object : List.of(root, item, micronutrients)) {
            List<String> properties = new ArrayList<>();
            object.path("properties").fieldNames().forEachRemaining(properties::add);
            List<String> required = new ArrayList<>();
            object.path("required").forEach(n -> required.add(n.asText()));

            assertThat(required).containsExactlyInAnyOrderElementsOf(properties);
            assertThat(object.path("additionalProperties").asBoolean(true)).isFalse();
        }
        List<String> keys = new ArrayList<>();
        micronutrients.path("properties").fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyElementsOf(Arrays.stream(Micronutrient.values()).map(Micronutrient::getKey).toList());

        List<String> order = new ArrayList<>();
        item.path("properties").fieldNames().forEachRemaining(order::add);
        assertThat(order.indexOf("fiberPer100g")).isLessThan(order.indexOf(AiDishResponse.MICRONUTRIENTS_FIELD));
        assertThat(order.indexOf(AiDishResponse.MICRONUTRIENTS_FIELD)).isEqualTo(order.indexOf("aiConfidence") - 1);
        // В обычной схеме микронутриентов нет
        assertThat(getDishSchema().path("properties").has(AiDishResponse.MICRONUTRIENTS_FIELD)).isFalse();
    }

    @Test
    void micronutrientTotalsFollowWeight() {
        DishDto dto = new AiDishResponse.AiDish(null, "Лосось", "🐟", Dish.FoodCategory.MAIN_COURSE,
                150, 1, 200.0, 20.0, 13.0, 0.0, 0.0,
                Map.of("omega3G", 2.0, "vitaminDMcg", 11.0, "sodiumMg", 60.0, "ironMg", -1.0, "unknownKey", 5.0), 80).toDishDto();

        assertThat(dto.getFiber()).isZero();
        assertThat(dto.getMicronutrients())
                .containsEntry("omega3G", 3.0)
                .containsEntry("vitaminDMcg", 16.5)
                .containsEntry("sodiumMg", 90.0)
                .doesNotContainKeys("ironMg", "unknownKey");
    }
}
