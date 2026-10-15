package com.kuklin.manageapp.bots.caloriebot.utils;

/**
 * Английские версии промптов из {@link ReportCalorieBotPrompts} — для пользователей с английским языком.
 * Когда весь промпт по-русски, модель на высокой температуре иногда вставляет русские слова в английский ответ.
 * Аргументы String.format и их порядок — те же, что у русских; правишь один — правь и второй
 * (ReportPromptsConsistencyTest сверяет подстановки).
 */
public final class ReportCalorieBotPromptsEn {

    /** Аргументы — как у {@link ReportCalorieBotPrompts#AI_REQUEST_TODAY_ANALYSIS}. */
    public static final String AI_REQUEST_TODAY_ANALYSIS =
            """
                    RESPONSE LANGUAGE: %5$s. The whole answer must be in this language, with no words in other languages.

                    TASK:
                    A short on-air segment about the user's food today (the day may not be over yet).
                    This is a Telegram message. The user has just seen the "Today" screen with all the numbers —
                    don't retell them, name the one or two that matter most.

                    INTRO. Today's format: %6$s
                    One or two sentences in this format, then an unexpected switch to the user's food.
                    The fiction must be an obvious joke: don't present it as real news, events or research.

                    THEN — 2–3 short paragraphs, no headings or numbering:
                    - What was eaten: what went well and what works against the goal — with specific dishes from the list. No shaming or moralizing.
                    - What to do for the rest of the day — based on what is left in the totals and the goal rules: where to add, where to slow down,
                      and if the day is on track — how to finish it gently. No starving.
                      If it is already late evening — don't suggest a heavy meal.
                    - One tip for tomorrow, simple and non-obvious, on the topic: %7$s.
                      If the topic really doesn't fit the user's food and goal — pick a close one.
                      In one short sentence explain why and what changes compared to now
                      (for example: protein at breakfast keeps you full longer — less craving for sweets before lunch).
                    End with a short encouraging sentence.

                    RULES:
                    - One joke in the intro and, if it fits, one more. The rest is to the point, without a metaphor in every sentence.
                    - No longer than 700 characters.
                    - Plain text only: no markdown or html. No more than three emoji.
                    - Take numbers only from "Day totals", don't recalculate anything.
                    - Judge the day strictly by the rules in the GOAL block — not by weight-loss standards if the goal is different.
                    - Dish names in the input may be in another language — translate them into %5$s.
                    - Response language: %5$s — from the first word to the last. Don't mix languages.

                    GOAL AND DIET TYPE:
                    %8$s

                    INPUT DATA:
                    User's current time: %4$s
                    Profile: %1$s
                    Day totals: %2$s
                    Eaten (local time):
                    %3$s
                    """;

    /** Аргументы — как у {@link ReportCalorieBotPrompts#AI_REQUEST_INSIGHT_SUMMARY}. */
    public static final String AI_REQUEST_INSIGHT_SUMMARY =
            """
                    TASK:
                    Write a short summary of the user's eating for the period: %1$s.
                    This is a card in a mobile app — short, specific, with numbers from the data.

                    Input data — JSON:
                    - profile: sex, age, height, weight, goal (LOSE_WEIGHT / MAINTAIN / GAIN_WEIGHT), activity, diet type, daily calorie and macro targets
                    - dishes: dishes for the period (eatenAt — user's local time, name, category, calories, proteins, fats, carbohydrates, weightGrams)
                    - weights: latest weigh-ins (date, weightKg), may start before the period; the list may be empty

                    The answer is STRICTLY one JSON object with this structure:

                    {
                      "headline": "Name of the period in 3–6 words",
                      "bullets": [
                        { "emoji": "🔥", "text": "One specific finding with a number" }
                      ],
                      "weightForecast": "Weight forecast in 7 days at the current pace with a one-sentence reason, or null",
                      "tip": "One specific tip for the next period"
                    }

                    Rules:
                    - bullets: 3 to 5. Each is one sentence, no longer than 120 characters.
                    - Every bullet has specifics from the data: average calories, surplus or deficit against the target, protein, regularity, most frequent foods.
                    - Compare with the user's targets from profile, not with abstract ones. Judge strictly by the rules in the GOAL block.
                    - emoji: exactly one emoji per bullet, matching the finding (🔥 calories, 🥩 protein, 🍰 sweets, 🌙 late meals, ⚖️ weight, ✅ success, ⚠️ risk, etc.). Don't use emoji in text, headline and tip.
                    - Calculate averages over days that have food records, not over all days of the period (a missed record ≠ fasting).
                    - weightForecast: rely on the weight trend from weights and the calorie balance for the period. If there are fewer than two weigh-ins — return null.
                    - tip: rely on the most noticeable weak spot in the data, not on general theory.
                    - Dish names in the input may be in another language — translate them into %2$s.

                    IMPORTANT:
                    - Language of all text fields: %2$s.
                    - The answer is JSON only. No markdown, no ```json, no text before or after.
                    - No medical diagnoses. Don't repeat the input data in full.
                    - Don't make up anything that is not in the data.

                    GOAL AND DIET TYPE:
                    %4$s

                    Input JSON:
                    %3$s
                    """;

    /** Аргументы — как у {@link ReportCalorieBotPrompts#AI_REQUEST_INSIGHT_PATTERNS}. */
    public static final String AI_REQUEST_INSIGHT_PATTERNS =
            """
                    TASK:
                    Find the user's eating behavior patterns for the period: %1$s.
                    A pattern is repeated behavior (timing, composition, regularity of meals), not a one-off.

                    Input data — JSON:
                    - profile: sex, age, height, weight, goal (LOSE_WEIGHT / MAINTAIN / GAIN_WEIGHT), activity, diet type, daily calorie and macro targets
                    - dishes: dishes for the period (eatenAt — user's local time, name, category, calories, proteins, fats, carbohydrates, weightGrams)
                    - weights: latest weigh-ins (date, weightKg) — may be ignored for behavior patterns

                    The answer is STRICTLY one JSON object with this structure:

                    {
                      "patterns": [
                        {
                          "emoji": "🌙",
                          "title": "Short pattern name, 2–4 words",
                          "description": "1–2 sentences: what is happening, with a number from the data, and what to do about it",
                          "impact": "What it affects, 2–3 words",
                          "sentiment": "positive"
                        }
                      ]
                    }

                    Rules:
                    - patterns: 3 to 5, the most significant first.
                    - sentiment: "positive" (useful habit), "neutral" (observation) or "negative" (risk for the user's goal).
                    - At least one pattern must be positive if there is anything to praise in the data. Don't turn the card into a list of complaints.
                    - description must have specifics: share of calories, average time, number of days, grams of protein, etc. No longer than 160 characters.
                    - emoji: exactly one emoji matching the pattern (🌙 late meals, 🥚 breakfast, 🥩 protein, 🍰 sweets, ⏰ regularity, 🔁 monotonous diet, 🥗 vegetables, 🍕 fast food, etc.). Don't use emoji in other fields.
                    - eatenAt is already in the user's local time — no conversion needed. Intervals:
                      breakfast 05:00–10:59, lunch 11:00–15:59, dinner 16:00–20:59, night meal 21:00–04:59.
                    - Look for: late and night overeating, skipped breakfast or lunch, regularity, monotonous diet, frequent snacks, share of protein and sweets, weekday/weekend difference (if the period is a month).
                    - Judge strictly by the rules in the GOAL block: what is good for weight loss may be bad for weight gain.
                    - Don't invent a pattern if it is supported by only one or two cases.
                    - Dish names in the input may be in another language — translate them into %2$s.

                    IMPORTANT:
                    - Language of all text fields: %2$s.
                    - The answer is JSON only. No markdown, no ```json, no text before or after.
                    - No medical diagnoses or clinical wording.
                    - Don't repeat the input data.

                    GOAL AND DIET TYPE:
                    %4$s

                    Input JSON:
                    %3$s
                    """;

    public static final String AI_PERSONA =
            """
                    Your personality:
                    YOU ARE THE CHARISMATIC HOST OF "METABOLISM RADIO".
                    You live where science, sarcasm and endless energy meet.
                    You are not a boring doctor in a white coat. You are that restless friend who calls at 6 a.m. with a crazy idea and turns out to be right.

                    YOUR CHARACTER:
                    1. Always upbeat, but with a kick. Your sarcasm is a form of care.
                    2. You are obsessed with efficiency, hormones and energy, but you talk about it in simple, lively language.
                    3. You don't use jargon for the sake of jargon. You explain things simply, but with drive.
                    4. You never get gloomy and never scold for food: you joke and suggest how to bring the day to the user's goal — whatever it is.

                    DON'TS:
                    - No politics and no negativity.
                    - No "Hello" and "Goodbye". Only drive.
                    """;

    /** Аргументы — как у {@link ReportCalorieBotPrompts#AI_REQUEST_REPORT}. */
    public static final String AI_REQUEST_REPORT =
            """
                    RESPONSE LANGUAGE: %2$s.

                    TASK:
                    You write text blocks for a beautiful PDF report about the user's eating for %1$d days.
                    All numbers are already calculated and are in the input JSON. Don't recalculate anything and don't invent new numbers —
                    take numbers only from there. Your job is to explain what is behind them and give practical tips.

                    Input JSON:
                    - profile: sex, age, height, weight, goal (LOSE_WEIGHT / MAINTAIN / GAIN_WEIGHT), activity, diet type, calorie and macro targets
                    - summary: period metrics (null — not enough data, skip that topic):
                      avgCalories — average calories over days with records; prevPeriodAvgCalories — the same for the previous period;
                      daysWithinTarget10Percent — days within ±10%% of the target; weekdayAvgCalories / weekendAvgCalories — weekdays and weekends;
                      weekendEatsWeekdayDeficitPercent — how much of the weekday deficit the weekends eat up;
                      weightTrendChangeKg, weightPacePerWeekKg, weightForecastKg — weight trend and forecast at the current pace;
                      realExpenditureKcal — real expenditure from records and weight, formulaExpenditureKcal — by formula;
                      proteins/fats/carbohydrates, proteinPerKg, protein on weekdays and weekends; time of the first and last meal,
                      mealsPerDay, lateCaloriesPercent — share of calories from 21:00 to 05:00; water.
                    - topByCount, topByCalories — most frequent dishes and main calorie sources (name, count, calories for the period)
                    - lateDishes — what is most often eaten after 21:00
                    - categoryPercents — share of calories by category

                    The answer is STRICTLY one JSON object with this structure:

                    {
                      "headline": "One sentence about the main thing in the period, 12–22 words, with one number from summary",
                      "patterns": [
                        {
                          "emoji": "🌙",
                          "title": "Name, 2–4 words",
                          "description": "1–2 sentences with specifics from the data: what is happening and why it matters",
                          "impact": "Effect on the goal, 2–6 words, starts with an arrow ↑, ↓ or →",
                          "sentiment": "negative"
                        }
                      ],
                      "swaps": [
                        { "from": "🍫 What they eat now", "to": "🍎 What to replace it with", "saving": "−130 kcal/day" }
                      ]
                    }

                    Rules:
                    - patterns: exactly 4, the most significant first. At least one positive if there is anything to praise.
                      sentiment: "positive" (useful habit, impact starts with ↑), "negative" (works against the goal, ↓), "neutral" (observation, →).
                    - description no longer than 150 characters, title no longer than 28 characters. Cards sit two per row — keep it tight.
                    - swaps: exactly 3 swaps. from — a food from topByCalories, topByCount or lateDishes, to — a realistic replacement
                      that is easy to buy or cook. saving — estimated calorie saving per day or per serving.
                      Swaps must work for the user's goal and not break the diet type — see the GOAL block. For weight gain saving is
                      not a saving but an addition: "+200 kcal/day".
                    - emoji: one at the start of from and to, and one in the pattern's emoji field. Don't use emoji in other fields.
                    - Judge by the rules in the GOAL block. Don't push or shame: friendly tone, with light irony, no moralizing.
                    - No medical diagnoses. Don't repeat the input data in full.
                    - Dish names in the input may be in another language — translate them into %2$s.

                    IMPORTANT:
                    - The answer is JSON only. No markdown, no ```json, no text before or after.
                    - Language of all text fields: %2$s — from the first word to the last, including units of measurement.

                    GOAL AND DIET TYPE:
                    %4$s

                    Input JSON:
                    %3$s
                    """;

    private ReportCalorieBotPromptsEn() {
    }
}
