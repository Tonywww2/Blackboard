package com.tonywww.blackboard;

import com.tonywww.blackboard.api.BlackboardApi;
import com.tonywww.blackboard.api.board.BlackboardType;
import com.tonywww.blackboard.api.board.GeneratorPool;
import com.tonywww.blackboard.api.question.AnswerResult;
import com.tonywww.blackboard.api.question.QuestionGenerator;
import com.tonywww.blackboard.api.question.Questions;
import com.tonywww.blackboard.api.registry.SimpleRegistry;
import com.tonywww.blackboard.chat.DefaultAnswerFormat;
import com.tonywww.blackboard.core.RewardKt;
import com.tonywww.blackboard.core.SelectionKt;
import com.tonywww.blackboard.validation.Validators;
import kotlin.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compile-and-run check that the public API is usable from plain Java exactly as the README's Java
 * snippets show it (built-in helpers and custom lambdas, for both generators and blackboard types).
 * Uses {@link BlackboardApi#id} so no version-specific {@code ResourceLocation} construction is needed.
 */
class JavaApiExampleTest {

    @Test
    void generatorWithBuiltinValidator() {
        ResourceLocation id = BlackboardApi.id("jtest_builtin");
        QuestionGenerator gen = QuestionGenerator.builder(id)
                .tag(BlackboardApi.BlackboardTags.MATH, BlackboardApi.BlackboardTags.DEFAULT)
                .weight(7)
                .generate(ctx -> {
                    int a = ctx.getRandom().nextIntBetweenInclusive(1, 9);
                    return Questions.builder(id)
                            .content(Component.literal(a + " + 1 = ?"))
                            .store("answer", a + 1)
                            .build();
                })
                .validate(Validators.INSTANCE.number("answer", 1e-9))
                .build();

        assertEquals(id, gen.getId());
        assertTrue(gen.getTags().contains(BlackboardApi.BlackboardTags.MATH));
    }

    @Test
    void generatorWithCustomLambdas() {
        ResourceLocation id = BlackboardApi.id("jtest_custom");
        QuestionGenerator gen = QuestionGenerator.builder(id)
                .tag(BlackboardApi.id("vanilla"))
                .weight(3)
                .generate(ctx -> Questions.builder(id)
                        .content(Component.translatable("question.mymod.diamond_tool"))
                        .store("answer", "pickaxe")
                        .build())
                .validate((question, ans) ->
                        ans.getText().trim().equalsIgnoreCase(question.getString("answer"))
                                ? AnswerResult.correct()
                                : AnswerResult.incorrect())
                .build();

        SimpleRegistry<QuestionGenerator> reg = new SimpleRegistry<>("jtest");
        reg.register(gen.getId(), gen, gen.getTags());
        assertTrue(reg.contains(id));
    }

    @Test
    void blackboardTypeWithBuiltinsAndCustomLambdas() {
        // Built-in selector + reward.
        BlackboardType builtin = BlackboardType.builder(BlackboardApi.id("jtest_type_builtin"))
                .pool(new GeneratorPool.ByTag(BlackboardApi.BlackboardTags.MATH))
                .selector((candidates, ctx) -> SelectionKt.weightedRandomSelect(candidates, ctx))
                .onSolved(rc -> {
                    RewardKt.defaultReward(rc);
                    return Unit.INSTANCE;
                })
                .rewardLootTable(BlackboardApi.id("rewards/default"))
                .answerFormat(DefaultAnswerFormat.INSTANCE)
                .maxAttempts(3)
                .build();
        assertEquals(BlackboardApi.id("jtest_type_builtin"), builtin.getId());

        // Custom selector (always the first candidate) + custom onSolved (message, then Unit).
        BlackboardType custom = BlackboardType.builder(BlackboardApi.id("jtest_type_custom"))
                .pool(GeneratorPool.All.INSTANCE)
                .selector((candidates, ctx) -> candidates.get(0).getGenerator())
                .onSolved(rc -> {
                    rc.getPlayer().sendSystemMessage(Component.literal("ok"));
                    return Unit.INSTANCE;
                })
                .answerFormat(DefaultAnswerFormat.INSTANCE)
                .maxAttempts(0)
                .build();
        assertEquals(GeneratorPool.All.INSTANCE, custom.getPool());
    }
}
