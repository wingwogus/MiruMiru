package com.example.api.controller

import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Profile("local")
@Controller
class ChatTestPageController {

    @GetMapping("/chat-test.html", produces = [MediaType.TEXT_HTML_VALUE])
    fun page(): ResponseEntity<Resource> {
        val resource = ClassPathResource("chat/chat-test.html")
        if (!resource.exists()) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .cacheControl(CacheControl.noStore())
            .body(resource)
    }
}
