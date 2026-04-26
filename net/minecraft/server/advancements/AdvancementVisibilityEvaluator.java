package net.minecraft.server.advancements;

import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;

public class AdvancementVisibilityEvaluator {
    private static final int VISIBILITY_DEPTH = 2;

    private static AdvancementVisibilityEvaluator.VisibilityRule evaluateVisibilityRule(Advancement p_265736_, boolean p_265426_) {
        Optional<DisplayInfo> optional = p_265736_.display();
        if (optional.isEmpty()) {
            return AdvancementVisibilityEvaluator.VisibilityRule.HIDE;
        } else if (p_265426_) {
            return AdvancementVisibilityEvaluator.VisibilityRule.SHOW;
        } else {
            return optional.get().isHidden() ? AdvancementVisibilityEvaluator.VisibilityRule.HIDE : AdvancementVisibilityEvaluator.VisibilityRule.NO_CHANGE;
        }
    }

    private static boolean evaluateVisiblityForUnfinishedNode(Stack<AdvancementVisibilityEvaluator.VisibilityRule> p_265343_) {
        for (int i = 0; i <= 2; i++) {
            AdvancementVisibilityEvaluator.VisibilityRule advancementvisibilityevaluator$visibilityrule = p_265343_.peek(i);
            if (advancementvisibilityevaluator$visibilityrule == AdvancementVisibilityEvaluator.VisibilityRule.SHOW) {
                return true;
            }

            if (advancementvisibilityevaluator$visibilityrule == AdvancementVisibilityEvaluator.VisibilityRule.HIDE) {
                return false;
            }
        }

        return false;
    }

    private static boolean evaluateVisibility(
        AdvancementNode p_301282_,
        Stack<AdvancementVisibilityEvaluator.VisibilityRule> p_301009_,
        Predicate<AdvancementNode> p_265359_,
        AdvancementVisibilityEvaluator.Output p_265303_
    ) {
        boolean flag = p_265359_.test(p_301282_);
        AdvancementVisibilityEvaluator.VisibilityRule advancementvisibilityevaluator$visibilityrule = evaluateVisibilityRule(p_301282_.advancement(), flag);
        boolean flag1 = flag;
        p_301009_.push(advancementvisibilityevaluator$visibilityrule);

        for (AdvancementNode advancementnode : p_301282_.children()) {
            flag1 |= evaluateVisibility(advancementnode, p_301009_, p_265359_, p_265303_);
        }

        boolean flag2 = flag1 || evaluateVisiblityForUnfinishedNode(p_301009_);
        p_301009_.pop();
        p_265303_.accept(p_301282_, flag2);
        return flag1;
    }

    public static void evaluateVisibility(AdvancementNode p_301203_, Predicate<AdvancementNode> p_265561_, AdvancementVisibilityEvaluator.Output p_265381_) {
        AdvancementNode advancementnode = p_301203_.root();
        Stack<AdvancementVisibilityEvaluator.VisibilityRule> stack = new ObjectArrayList<>();

        for (int i = 0; i <= 2; i++) {
            stack.push(AdvancementVisibilityEvaluator.VisibilityRule.NO_CHANGE);
        }

        evaluateVisibility(advancementnode, stack, p_265561_, p_265381_);
    }

    public static boolean isVisible(AdvancementNode advancement, Predicate<AdvancementNode> test) {
        Stack<AdvancementVisibilityEvaluator.VisibilityRule> stack = new ObjectArrayList<>();

        for(int i = 0; i <= 2; ++i) {
            stack.push(AdvancementVisibilityEvaluator.VisibilityRule.NO_CHANGE);
        }
        return evaluateVisibility(advancement.root(), stack, test, (adv, visible) -> {});
    }

    @FunctionalInterface
    public interface Output {
        void accept(AdvancementNode p_300909_, boolean p_265580_);
    }

    static enum VisibilityRule {
        SHOW,
        HIDE,
        NO_CHANGE;
    }
}
