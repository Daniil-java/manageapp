package com.kuklin.manageapp.bots.caloriebot.report.dto;

import com.kuklin.manageapp.bots.caloriebot.dish.Micronutrient;

import java.util.List;

/**
 * Клетчатка и микронутриенты за период — средние в день против нормы. Считаются кодом
 * ({@link com.kuklin.manageapp.bots.caloriebot.report.NutrientStatsCalculator}), ИИ получает их готовыми.
 * Значения в блюдах — оценка ИИ, поэтому в отчёте главное — вывод {@link Status}, а не точная цифра.
 *
 * @param fiber              null — ни у одного блюда периода клетчатка не оценена (старые записи)
 * @param micronutrients     пусто — микронутриенты не оценены (не Premium или старые записи)
 * @param micronutrientDays  по скольким дням посчитаны микронутриенты
 * @param loggedDays         дней с записями еды вообще — для подписи «по N дн. из M»
 */
public record NutrientStats(FiberStat fiber,
                            List<MicronutrientStat> micronutrients,
                            int micronutrientDays,
                            int loggedDays) {

    /** Насколько среднее совпало с нормой */
    public enum Status {
        /** Меньше 70% нормы, которую стоит набрать */
        LOW,
        OK,
        /** Больше верхнего предела */
        HIGH
    }

    /**
     * @param days по скольким дням посчитано (дни, где у блюд есть клетчатка)
     */
    public record FiberStat(int avgGrams, int targetGrams, int days, Status status) {
    }

    /** avg и norm — в единицах nutrient.getUnit() */
    public record MicronutrientStat(Micronutrient nutrient, double avg, double norm, Status status) {
        public int getPercent() {
            return norm > 0 ? (int) Math.round(avg * 100 / norm) : 0;
        }
    }
}
