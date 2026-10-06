package com.roomlog.global.config;

import com.roomlog.defect.domain.RepairSupply;
import com.roomlog.defect.repository.RepairSupplyRepository;
import com.roomlog.global.infra.KakaoImageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 하자 종류별 자가 수리 준비물 기본 데이터.
 * purchase_url은 우선 쿠팡 검색 링크로 넣어두고, 실제 상품 링크는 운영 중 채워 넣는다.
 * 이미지는 쿠팡 검색어와 같은 검색어로 카카오 이미지 검색을 해서 채운다(쿠팡은 공개 API가 없다).
 * 이미지가 비어 있는 행만 기동할 때마다 채우므로, 운영 중 직접 넣은 이미지는 덮어쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RepairSupplyInitializer implements ApplicationRunner {

    private static final String COUPANG_SEARCH_URL = "https://www.coupang.com/np/search?q=";

    private final RepairSupplyRepository repairSupplyRepository;
    private final KakaoImageClient kakaoImageClient;

    @Override
    public void run(ApplicationArguments args) {
        seedIfEmpty();
        fillMissingImages();
    }

    private void seedIfEmpty() {
        if (repairSupplyRepository.count() > 0) return;

        repairSupplyRepository.saveAll(List.of(
                supply("SCRATCH", "벽지 보수용 스티커", 7900, "벽지 보수 스티커", 1),
                supply("SCRATCH", "가구 흠집 보수 크레용", 6900, "가구 흠집 보수 크레용", 2),
                supply("SCRATCH", "아크릴 물감 세트", 5500, "아크릴 물감 세트", 3),

                supply("CRACK", "벽면 크랙 보수제(퍼티)", 9900, "벽 크랙 보수제 퍼티", 1),
                supply("CRACK", "보수용 헤라", 3500, "퍼티 헤라", 2),
                supply("CRACK", "사포 세트", 4000, "사포 세트", 3),

                supply("PEELING", "벽지 전용 풀", 5900, "벽지 풀", 1),
                supply("PEELING", "실리콘 실란트", 6500, "실리콘 실란트", 2),
                supply("PEELING", "실리콘 건", 8900, "실리콘 건", 3),

                supply("STAIN", "곰팡이 제거제", 8900, "곰팡이 제거제", 1),
                supply("STAIN", "다목적 세정제", 6500, "다목적 세정제", 2),
                supply("STAIN", "방수 코팅 스프레이", 12000, "방수 코팅 스프레이", 3),

                // 카카오 이미지 검색이 엉뚱한 사진(유튜브 썸네일, 블로그 배너)을 돌려줘서 제품 사진으로 고정
                supply("BREAKAGE", "만능 접착제", 7500, "만능 접착제", 1,
                        "https://st.kakaocdn.net/shophow/p/B5266708563.jpg?ut=20260221001830"),
                supply("BREAKAGE", "보수용 퍼티", 9900, "보수용 퍼티", 2,
                        "https://st.kakaocdn.net/shophow/p/A5266741072.jpg?ut=20260221002338")
        ));
    }

    private void fillMissingImages() {
        List<RepairSupply> missing = repairSupplyRepository.findByImageUrlIsNull();
        for (RepairSupply supply : missing) {
            String imageUrl = kakaoImageClient.searchFirstImageUrl(searchKeywordOf(supply));
            if (imageUrl != null) {
                supply.updateImageUrl(imageUrl);
            }
        }
        repairSupplyRepository.saveAll(missing);
    }

    /** 쿠팡 검색 링크면 그 검색어를, 실제 상품 링크로 바뀌었으면 상품명을 쓴다. */
    private String searchKeywordOf(RepairSupply supply) {
        String url = supply.getPurchaseUrl();
        if (url != null && url.startsWith(COUPANG_SEARCH_URL)) {
            return URLDecoder.decode(url.substring(COUPANG_SEARCH_URL.length()), StandardCharsets.UTF_8);
        }
        return supply.getName();
    }

    private RepairSupply supply(String defectType, String name, int price, String searchKeyword, int sortOrder) {
        return supply(defectType, name, price, searchKeyword, sortOrder, null);
    }

    /** imageUrl을 주면 기동 시 이미지 검색을 건너뛰고 그 사진을 그대로 쓴다. */
    private RepairSupply supply(String defectType, String name, int price, String searchKeyword, int sortOrder,
                                String imageUrl) {
        return RepairSupply.builder()
                .defectType(defectType)
                .name(name)
                .price(price)
                .imageUrl(imageUrl)
                .purchaseUrl(COUPANG_SEARCH_URL + URLEncoder.encode(searchKeyword, StandardCharsets.UTF_8))
                .sortOrder(sortOrder)
                .build();
    }
}
