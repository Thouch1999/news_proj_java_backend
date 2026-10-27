package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.common.response.SuccessResponse;
import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import com.api.v1.newssystem.model.request.PromoImpressionRequest;
import com.api.v1.newssystem.model.response.PromoDto;
import com.api.v1.newssystem.security.ClientIpResolver;
import com.api.v1.newssystem.service.PromoService;
import com.api.v1.newssystem.util.ApiResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

// The sponsors on the public pages, no login. The path says "promos" on purpose: ad blockers block "ads" and "sponsor".
@RestController
@RequestMapping("/api/v1/promos")
@RequiredArgsConstructor
public class PromoController {
    private final PromoService promoService;
    private final ClientIpResolver clientIpResolver;

    // One sponsor for a placement, or an empty payload when there is none. Never cached: the pick is random.
    @GetMapping
    public ResponseEntity<SuccessResponse<Object>> pick(@RequestParam String placement,
                                                          @RequestParam(required = false) String categorySlug,
                                                          @RequestParam(required = false) String lang) {
        // the sidebar answers a slideshow (settings and a list of sponsors), the popup and the bar exactly one sponsor
        Object payload = SponsorPlacement.parse(placement).filter(found -> found == SponsorPlacement.ARTICLE_SIDEBAR).isPresent()
                ? promoService.pickSidebar(categorySlug, lang).orElse(null)
                : promoService.pick(placement, categorySlug, lang).orElse(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponseUtil.success(HttpStatus.OK, payload));
    }

    // The page calls this when the promo is really seen. Bots and floods get the same answer and are not counted.
    @PostMapping("/{id}/impression")
    public ResponseEntity<SuccessResponse<Void>> impression(@PathVariable Long id,
                                                            @Valid @RequestBody PromoImpressionRequest request,
                                                            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                                            HttpServletRequest http) {
        promoService.recordImpression(id, request.getPlacement(), clientIpResolver.resolve(http), userAgent);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }

    // A video starts playing / reaches its end (the page sends each once per page view). Same filtering as the impressions.
    @PostMapping("/{id}/video-play")
    public ResponseEntity<SuccessResponse<Void>> videoPlay(@PathVariable Long id,
                                                           @Valid @RequestBody PromoImpressionRequest request,
                                                           @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                                           HttpServletRequest http) {
        promoService.recordVideoPlay(id, request.getPlacement(), clientIpResolver.resolve(http), userAgent);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }

    @PostMapping("/{id}/video-complete")
    public ResponseEntity<SuccessResponse<Void>> videoComplete(@PathVariable Long id,
                                                               @Valid @RequestBody PromoImpressionRequest request,
                                                               @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                                               HttpServletRequest http) {
        promoService.recordVideoComplete(id, request.getPlacement(), clientIpResolver.resolve(http), userAgent);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }

    // Counts the click and redirects (302) to the address saved with the sponsor: nothing from the request is used as the target.
    @GetMapping("/{id}/click")
    public ResponseEntity<Void> click(@PathVariable Long id,
                                      @RequestParam String placement,
                                      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                      HttpServletRequest http) {
        String target = promoService.recordClick(id, placement, clientIpResolver.resolve(http), userAgent);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
