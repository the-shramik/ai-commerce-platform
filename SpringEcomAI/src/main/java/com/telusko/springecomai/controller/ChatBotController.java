package com.telusko.springecomai.controller;

import com.telusko.springecomai.model.dto.BotResponse;
import com.telusko.springecomai.service.ChatBotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin("*")
public class ChatBotController {

    @Autowired
    private ChatBotService chatBotService;

    @GetMapping("/ask")
    public ResponseEntity<BotResponse> askBot(@RequestParam String message) {
        String reply = chatBotService.getBotResponse(message);
        return ResponseEntity.ok(new BotResponse(reply));
    }
}
