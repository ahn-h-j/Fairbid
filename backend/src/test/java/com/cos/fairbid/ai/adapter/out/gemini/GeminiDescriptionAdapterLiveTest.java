package com.cos.fairbid.ai.adapter.out.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.cos.fairbid.ai.adapter.out.guardrail.rules.DescriptionQualityRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.HookRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.PersonaRule;
import com.cos.fairbid.ai.adapter.out.guardrail.rules.ReformatRule;
import com.cos.fairbid.ai.application.dto.AiAssistCommand;
import com.cos.fairbid.ai.application.dto.PriceItem;
import com.cos.fairbid.ai.application.dto.ProductAnalysis;
import com.cos.fairbid.ai.domain.AiAssistResult;
import com.cos.fairbid.ai.domain.SuggestedPrices;
import com.cos.fairbid.auction.domain.Category;

/**
 * {@link GeminiDescriptionAdapter} 실측 테스트.
 *
 * <p>{@code DESCRIPTION_LIVE_TEST=true} + {@code GEMINI_API_KEY} 환경변수가 있을 때만 동작.
 * SPEC §19 옵션 B 이슈 #91 의 완료 조건 검증용 (응답 시간 / 가드레일 위반율 / 설명 길이).</p>
 *
 * <p>샘플 3건 (iphone / basketball / taylormade) 을 순차 호출하고 콘솔에 요약을 출력한다.</p>
 */
@EnabledIfEnvironmentVariable(named = "DESCRIPTION_LIVE_TEST", matches = "true")
class GeminiDescriptionAdapterLiveTest {

    @Test
    void generateDescription_liveSamples() {
        GeminiDescriptionProperties props = new GeminiDescriptionProperties();
        props.setApiKey(System.getenv("GEMINI_API_KEY"));
        String modelOverride = System.getenv("AI_DESCRIPTION_GEMINI_MODEL");
        if (modelOverride != null && !modelOverride.isBlank()) {
            props.setModel(modelOverride);
        }
        assertThat(props.getApiKey()).as("GEMINI_API_KEY 필요").isNotBlank();

        GeminiDescriptionPromptBuilder builder = new GeminiDescriptionPromptBuilder(props);
        builder.loadSystemPrompt();
        GeminiDescriptionAdapter adapter = new GeminiDescriptionAdapter(props, builder);

        DescriptionQualityRule qualityRule = new DescriptionQualityRule();
        HookRule hookRule = new HookRule();
        PersonaRule personaRule = new PersonaRule();
        ReformatRule reformatRule = new ReformatRule();

        List<Sample> samples = List.of(
                new Sample(
                        "iphone-15-pro",
                        new AiAssistCommand(
                                Category.ELECTRONICS,
                                "상품 정보: 아이폰 15 Pro 256GB 블루 티타늄\n구매 시기: 2024년 3월\n상태: 거의 새것 (사용 흔적 미미)\n추가 정보: 정품 박스, 라이트닝 어댑터 없음, 케이스·강화유리 부착 사용",
                                List.of()),
                        new ProductAnalysis("아이폰 15 Pro 256GB 블루 티타늄", "A",
                                "거의 새것, 사용 흔적 미미", "아이폰 15 Pro 256GB", "iphone_15_pro_256gb"),
                        new SuggestedPrices(900_000L, 1_000_000L, 1_100_000L)),
                new Sample(
                        "basketball",
                        new AiAssistCommand(
                                Category.SPORTS,
                                "상품 정보: 몰텐 BG5000 공인구 7호\n구매 시기: 2023년 6월\n상태: 양호 (체육관 실내 사용, 그립감 그대로)\n추가 정보: 공기 빵빵, 에어펌프 포함",
                                List.of()),
                        new ProductAnalysis("몰텐 BG5000 공인구 7호", "B",
                                "양호, 체육관 실내 사용", "몰텐 BG5000", "molten_bg5000_7"),
                        new SuggestedPrices(32_000L, 38_000L, 46_000L)),
                new Sample(
                        "taylormade-stealth2",
                        new AiAssistCommand(
                                Category.SPORTS,
                                "상품 정보: TaylorMade Stealth2 드라이버 10.5도 SR\n구매 시기: 2023년 4월\n상태: 양호 (약 15라운드 사용, 헤드 페이스 미세 자국)\n추가 정보: 정품 헤드커버, 그립 교체 1회",
                                List.of()),
                        new ProductAnalysis("TaylorMade Stealth2 드라이버 10.5도 SR", "B",
                                "양호, 약 15라운드 사용", "taylormade_stealth2_10_5_sr",
                                "taylormade_stealth2_10_5_sr"),
                        new SuggestedPrices(270_000L, 290_000L, 320_000L))
        );

        StringBuilder summary = new StringBuilder("\n=== GeminiDescriptionAdapter Live ===\n");
        summary.append("model=").append(props.getModel()).append('\n');

        for (Sample s : samples) {
            long t0 = System.currentTimeMillis();
            String description = adapter.generateDescription(s.command, s.analysis, s.prices, null);
            long elapsed = System.currentTimeMillis() - t0;

            assertThat(description).as(s.id + " 설명 반환").isNotBlank();

            AiAssistResult fakeResult = new AiAssistResult(s.prices, description, "high", null);
            List<PriceItem> emptyItems = List.of();
            int violations = qualityRule.check(fakeResult, s.command, emptyItems).size()
                    + hookRule.check(fakeResult, s.command, emptyItems).size()
                    + personaRule.check(fakeResult, s.command, emptyItems).size()
                    + reformatRule.check(fakeResult, s.command, emptyItems).size();

            summary.append('\n')
                    .append("[").append(s.id).append("] elapsed_ms=").append(elapsed)
                    .append(" len=").append(description.length())
                    .append(" violations=").append(violations).append('\n')
                    .append(description).append('\n');
        }

        System.out.println(summary);
    }

    private record Sample(
            String id,
            AiAssistCommand command,
            ProductAnalysis analysis,
            SuggestedPrices prices
    ) {
    }
}
