package com.example.mcpserver.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class HtmlTextExtractor {

    public String extract(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script,style,noscript").remove();
        String clean = Jsoup.clean(doc.body().html(), Safelist.relaxed());
        return Jsoup.parse(clean).text();
    }
}
