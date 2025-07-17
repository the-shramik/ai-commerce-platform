package com.telusko.springecomai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatBotService {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private ResourceLoader resourceLoader;

    public String getBotResponse(String userQuery) {
        try {
            // Load prompt template from classpath resource
            String promptTemplate = Files.readString(
                    resourceLoader.getResource("classpath:prompts/chatbot-rag-prompt.st")
                            .getFile()
                            .toPath()
            );

            // Fetch similar content from the vector store using semantic search
            String context = fetchSemanticContext(userQuery);

            // Fill template variables with user query and relevant context
            Map<String, Object> variables = new HashMap<>();
            variables.put("userQuery", userQuery);
            variables.put("context", context);

            // Create a final prompt using the template and variables
            PromptTemplate prompt = PromptTemplate.builder()
                    .template(promptTemplate)
                    .variables(variables)
                    .build();

            // Call the chat model and return the generated response
            return chatClient.prompt(prompt.create()).call().content();

        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    // Use vector store to find semantically similar documents based on query
    private String fetchSemanticContext(String query) {
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(5)                       // get top 5 most similar documents
                        .similarityThreshold(0.7f)     // filter documents with similarity score below threshold
                        .build()
        );

        // Build a combined context string from document contents
        StringBuilder contextBuilder = new StringBuilder();
        for (Document doc : documents) {
            contextBuilder.append(doc.getFormattedContent()).append("\n");
        }

        return contextBuilder.toString();
    }
}
