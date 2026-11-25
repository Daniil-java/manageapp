package com.kuklin.manageapp.bots.caloriebot.dish.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kuklin.manageapp.aiconversation.models.openai.OpenAiChatCompletionRequest.ResponseFormat;
import com.kuklin.manageapp.bots.caloriebot.dish.Dish;
import com.kuklin.manageapp.bots.caloriebot.dish.Micronutrient;
import com.kuklin.manageapp.bots.caloriebot.dish.dto.DishDto;

import java.util.List;
import java.util.Map;

/**
 * Ответ ИИ при распознавании еды (фото, текст, голос) — строго по {@link #RESPONSE_FORMAT}.
 * <p>
 * Порядок полей в схеме важен: модель пишет ответ по порядку, поэтому сначала рассуждение (estimation),
 * потом вес, и только потом пищевая ценность. Пищевая ценность — на 100 г, итог на порцию считаем сами:
 * калории всегда сходятся с весом, и изменение порции в боте пересчитывает по той же плотности.
 *
 * @param dishes пустой список — еды нет
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiDishResponse(List<AiDish> dishes) {

    /**
     * Схема ответа (Structured Outputs). Корень — объект: голый массив strict-режим не принимает.
     */
    private static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "dishes": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "estimation": {"type": "string"},
                      "name": {"type": "string"},
                      "emojiIcon": {"type": "string"},
                      "category": {
                        "type": "string",
                        "enum": ["MAIN_COURSE", "SIDE_DISH", "SOUP", "SALAD", "SNACK", "DESSERT", "DRINK",
                                 "BREAKFAST_ITEM", "BREAD_BAKERY", "FAST_FOOD", "SAUCE_DIP", "UNKNOWN"]
                      },
                      "weightGrams": {"type": "integer"},
                      "portions": {"type": "integer"},
                      "caloriesPer100g": {"type": "number"},
                      "proteinsPer100g": {"type": "number"},
                      "fatsPer100g": {"type": "number"},
                      "carbohydratesPer100g": {"type": "number"},
                      "fiberPer100g": {"type": "number"},
                      "aiConfidence": {"type": "integer"}
                    },
                    "required": ["estimation", "name", "emojiIcon", "category", "weightGrams", "portions",
                                 "caloriesPer100g", "proteinsPer100g", "fatsPer100g", "carbohydratesPer100g",
                                 "fiberPer100g", "aiConfidence"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["dishes"],
              "additionalProperties": false
            }
            """;

    /** Поле Premium-схемы: витамины, минералы и подробности по жирам и сахару на 100 г */
    public static final String MICRONUTRIENTS_FIELD = "micronutrientsPer100g";

    public static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.makeJsonSchema("dishes", readSchema());

    /** Та же схема плюс {@value #MICRONUTRIENTS_FIELD} — для Premium. Имя другое, чтобы схемы не путались в логах. */
    public static final ResponseFormat RESPONSE_FORMAT_PREMIUM =
            ResponseFormat.makeJsonSchema("dishes_premium", buildPremiumSchema());

    private static ObjectNode readSchema() {
        try {
            // JsonNode сохраняет порядок полей — он и есть порядок, в котором модель пишет ответ
            return (ObjectNode) new ObjectMapper().readTree(SCHEMA_JSON);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid dish response schema", e);
        }
    }

    /**
     * Микронутриенты — после КБЖУ и клетчатки, перед aiConfidence: модель сначала оценивает основное.
     * Ключи — из {@link Micronutrient}, все обязательны (strict-режим не допускает необязательных).
     */
    private static ObjectNode buildPremiumSchema() {
        ObjectNode schema = readSchema();
        ObjectNode item = (ObjectNode) schema.at("/properties/dishes/items");

        ObjectNode micronutrients = item.objectNode().put("type", "object");
        ObjectNode nutrientProperties = micronutrients.putObject("properties");
        ArrayNode nutrientRequired = micronutrients.putArray("required");
        for (Micronutrient nutrient : Micronutrient.values()) {
            nutrientProperties.putObject(nutrient.getKey()).put("type", "number");
            nutrientRequired.add(nutrient.getKey());
        }
        micronutrients.put("additionalProperties", false);

        ObjectNode oldProperties = (ObjectNode) item.get("properties");
        ObjectNode properties = item.objectNode();
        oldProperties.fields().forEachRemaining(field -> {
            if (field.getKey().equals("aiConfidence")) properties.set(MICRONUTRIENTS_FIELD, micronutrients);
            properties.set(field.getKey(), field.getValue());
        });
        item.set("properties", properties);
        ((ArrayNode) item.get("required")).add(MICRONUTRIENTS_FIELD);
        return schema;
    }

    /**
     * Одно блюдо в ответе ИИ.
     *
     * @param estimation рассуждение об оценке веса («тарелка ~24 см, рис ~150 см³, ~200 г»); не сохраняется
     * @param weightGrams вес всей съеденной порции (всех штук), для напитков 1 мл = 1 г
     * @param portions    сколько штук/порций (3 печенья — 3)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiDish(
            String estimation,
            String name,
            String emojiIcon,
            Dish.FoodCategory category,
            Integer weightGrams,
            Integer portions,
            Double caloriesPer100g,
            Double proteinsPer100g,
            Double fatsPer100g,
            Double carbohydratesPer100g,
            Double fiberPer100g,
            Map<String, Double> micronutrientsPer100g,
            Integer aiConfidence
    ) {

        /**
         * Блюдо с пищевой ценностью на всю порцию: значения на 100 г × вес / 100.
         */
        public DishDto toDishDto() {
            int weight = weightGrams != null && weightGrams > 0 ? weightGrams : 1;
            int count = portions != null && portions > 0 ? portions : 1;
            return new DishDto()
                    .setName(name)
                    .setEmojiIcon(emojiIcon)
                    .setCategory(category != null ? category : Dish.FoodCategory.UNKNOWN)
                    .setWeight(weight)
                    .setPortions(count)
                    .setPortionWeight(Math.max(1, Math.round((float) weight / count)))
                    .setCalories(getTotal(caloriesPer100g, weight))
                    .setProteins(getTotal(proteinsPer100g, weight))
                    .setFats(getTotal(fatsPer100g, weight))
                    .setCarbohydrates(getTotal(carbohydratesPer100g, weight))
                    .setFiber(fiberPer100g != null ? getTotal(fiberPer100g, weight) : null)
                    .setMicronutrients(Micronutrient.buildTotalsOrNull(micronutrientsPer100g, weight))
                    .setAiConfidence(aiConfidence)
                    .setIsDish(true);
        }

        private static int getTotal(Double per100g, int weight) {
            if (per100g == null || per100g < 0) {
                return 0;
            }
            return (int) Math.round(per100g * weight / 100);
        }
    }
}
