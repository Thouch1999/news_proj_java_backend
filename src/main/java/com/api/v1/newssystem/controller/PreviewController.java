package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.model.response.PreviewDto;
import com.api.v1.newssystem.service.PreviewService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

// Link previews (Open Graph): small HTML pages for the bots of Facebook, Messenger, Telegram, X, LinkedIn ... that read
// the head of a shared link. Public (see SecurityConfig) and not part of the JSON API, so there is no response wrapper.
// An article that is not PUBLISHED (or does not exist) gets the home preview with 404: its title is never shown.
@Controller
@RequestMapping("/preview")
@RequiredArgsConstructor
public class PreviewController {
    private static final String VIEW = "preview/preview";
    private static final String CACHEABLE = "public, max-age=600";

    private final PreviewService previewService;

    @GetMapping
    public ModelAndView home(@RequestParam(required = false) String lang, HttpServletResponse response) {
        return page(previewService.home(lang), HttpStatus.OK, response);
    }

    @GetMapping("/news/{slug}")
    public ModelAndView news(@PathVariable String slug, @RequestParam(required = false) String lang, HttpServletResponse response) {
        return previewService.news(slug, lang)
                .map(preview -> page(preview, HttpStatus.OK, response))
                .orElseGet(() -> page(previewService.home(lang), HttpStatus.NOT_FOUND, response));
    }

    @GetMapping("/category/{slug}")
    public ModelAndView category(@PathVariable String slug, @RequestParam(required = false) String lang, HttpServletResponse response) {
        return previewService.category(slug, lang)
                .map(preview -> page(preview, HttpStatus.OK, response))
                .orElseGet(() -> page(previewService.home(lang), HttpStatus.NOT_FOUND, response));
    }

    // a 404 is not kept by caches: the article may be published a moment later
    private ModelAndView page(PreviewDto preview, HttpStatus status, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, status == HttpStatus.OK ? CACHEABLE : "no-cache");
        ModelAndView view = new ModelAndView(VIEW, "page", preview);
        view.setStatus(status);
        return view;
    }
}
