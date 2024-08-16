package com.example.demo.controller;

import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.URLDecoder;

@RestController
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);
    @GetMapping("/proxy")
    public ResponseEntity<byte[]> getProxyResponse(@RequestParam String url) {
        try {
            url = URLDecoder.decode(url, StandardCharsets.UTF_8.name());
            log.info("Decoding URL: " + url);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            
            headers.setAcceptCharset(java.util.Collections.singletonList(StandardCharsets.UTF_8));
            headers.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            headers.add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
            // headers.add("Connection", "keep-alive");
            // headers.add("Upgrade-Insecure-Requests", "1");
            // headers.add("Sec-Fetch-Dest", "document");
            // headers.add("Sec-Fetch-Mode", "navigate");
            // headers.add("Sec-Fetch-Site", "none");
            // headers.add("Sec-Fetch-User", "?1");
            // headers.setConnection("keep-alive");
            headers.add("Cookie", "_ga=GA1.1.1411928985.1723788857; _gid=GA1.1.668514027.1723788857; _ga_ZY32DVFY15=GS1.1.1723788857.1.0.1723788883.0.0.0");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
            url, 
            HttpMethod.GET, 
            entity, 
            byte[].class
            );

            // Detect charset from Content-Type header
            Charset charset = detectCharset(response);
            log.info("Detected charset: " + charset.name());
            
            String bodyString = new String(response.getBody(), charset);
            Document doc = Jsoup.parse(bodyString, url);

            Element metaCharset = doc.select("meta[charset]").first();
            if (metaCharset == null) {
                doc.head().prependChild(doc.createElement("meta").attr("charset", charset.name()));
            } else {
                metaCharset.attr("charset", charset.name());
            }
            String baseUri = doc.baseUri();
            log.info("base uri:"+ baseUri);
            
            // HTML redirect <a href> link
            for (Element link : doc.select("a")) {
                String href = link.attr("href");
                String newHref = createProxyUrl(baseUri, href);
                log.info("path:"+ href);
                log.info("new URL:"+ newHref);
                link.attr("href", newHref);
            }
            
            // handle image link
            for (Element imageLink : doc.select("img")) {
                //String src = imageLink.attr("src");
                //String newSrc = createAbsoluteUrl(baseUri, src);
                imageLink.attr("src", imageLink.absUrl("src"));
                // imageLink.attr("src", newSrc);
                // log.info("img url: "+newSrc);
            }
            // handle CSS link
            for (Element cssLink : doc.head().select("link")) {
                cssLink.attr("href", cssLink.absUrl("href"));
                // log.info("css url: " + cssUrl);
            }
            // handle JS link
            for (Element jsLink : doc.select("script[src]")) {
                jsLink.attr("abs:src", "/proxy?url=" + URLEncoder.encode(jsLink.absUrl("asbs:src"), StandardCharsets.UTF_8));
                // log.info("script url: " + jsUrl);
            }
            
            byte[] modifiedContent = doc.outerHtml().getBytes(charset);
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.TEXT_HTML);
            return new ResponseEntity<>(modifiedContent, responseHeaders, response.getStatusCode());
        } catch (Exception e) {
            log.error("Error in proxy request", e);
            return new ResponseEntity<>(("Error processing request: " + e.getMessage()).getBytes(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    private Charset detectCharset(ResponseEntity<byte[]> response) {
        // First, try to get charset from Content-Type header
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType != null && contentType.getCharset() != null) {
            return contentType.getCharset();
        }

        // If not found in header, try to detect from content
        byte[] content = response.getBody();
        String bodyStart = new String(content, 0, Math.min(content.length, 1000), StandardCharsets.UTF_8);
        Document doc = Jsoup.parse(bodyStart);
        Element metaCharset = doc.select("meta[charset], meta[http-equiv=content-type]").first();
        
        if (metaCharset != null) {
            String charsetName = metaCharset.hasAttr("charset") ? 
                metaCharset.attr("charset") : 
                metaCharset.attr("content").split("charset=")[1];
            try {
                return Charset.forName(charsetName);
            } catch (Exception e) {
                log.warn("Invalid charset detected: " + charsetName);
            }
        }

        // Default to UTF-8 if we couldn't detect the charset
        return StandardCharsets.UTF_8;
    }

    private String createProxyUrl(String baseUri, String path) {
        String absoluteUrl = createAbsoluteUrl(baseUri, path);
        return "/proxy?url=" + URLEncoder.encode(absoluteUrl, StandardCharsets.UTF_8);
    }

    private String createAbsoluteUrl(String baseUri, String path) {
        try {
            URI base = new URI(baseUri);
            if (path == null || path.isEmpty() || path.equals("/")) {
                return base.getScheme() + "://" + base.getAuthority();
            }
            if (path.startsWith("http://") || path.startsWith("https://")) {
                return path;
            }
            if (path.startsWith("//")) {
                return base.getScheme() + ":" + path;
            }
            if (path.startsWith("./")) {
                path = path.substring(2);
            }
            String basePath = base.getPath();
            if (basePath == null || basePath.isEmpty() || !basePath.endsWith("/")) {
                basePath = "/";
            }
            return new URI(base.getScheme(), base.getAuthority(), basePath + path, null, null).toString();
        } catch (URISyntaxException e) {
            log.error("Error resolving URL: base=" + baseUri + ", path=" + path, e);
            return path;
        }
    }
}
