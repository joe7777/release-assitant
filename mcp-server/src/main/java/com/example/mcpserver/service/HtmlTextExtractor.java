package com.example.mcpserver.service;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class HtmlTextExtractor {

    public String extract(String html) {
        return extract(html, "", List.of());
    }

    public String extract(String html, String contentCss, List<String> removeCss) {
        Document doc = Jsoup.parse(html);
        doc.select("script,style,noscript").remove();
        if (removeCss != null && !removeCss.isEmpty()) {
            doc.select(String.join(",", removeCss)).remove();
        }
        String targetHtml = extractContentHtml(doc, contentCss);
        String clean = Jsoup.clean(targetHtml, Safelist.relaxed());
        return Jsoup.parse(clean).text();
    }

    private String extractContentHtml(Document doc, String contentCss) {
        if (contentCss == null || contentCss.isBlank()) {
            Element body = doc.body();
            return body != null ? body.html() : "";
        }
        Elements content = doc.select(contentCss);
        if (content.isEmpty()) {
            Element body = doc.body();
            return body != null ? body.html() : "";
        }
        return content.html();
    }
}
