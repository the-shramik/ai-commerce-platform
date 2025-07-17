package com.telusko.springecomai.service;

import com.telusko.springecomai.model.Product;
import com.telusko.springecomai.repo.ProductRepo;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@Service
public class ProductService {

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private AIImageGeneratorService aiImageGeneratorService;

    public List<Product> getAllProducts() {
        return productRepo.findAll();
    }

    public Product getProductById(int id) {
        return productRepo.findById(id).orElse(new Product(-1));
    }

    public Product addOrUpdateProduct(Product product, MultipartFile image) throws IOException {

        if (image != null && !image.isEmpty()) {
            product.setImageName(image.getOriginalFilename());
            product.setImageType(image.getContentType());
            product.setProductImage(image.getBytes());

        }

        Product savedProduct = productRepo.save(product);

        // Prepare content for semantic embedding (RAG)
        String contentToEmbed =String.format("""
         Product Name: %s
         Description: %s
         Brand: %s
         Category: %s
         Price: %.2f
         Release Date: %s
         Available: %s
         Stock: %d
        """,
                savedProduct.getName(),
                savedProduct.getDescription(),
                savedProduct.getBrand(),
                savedProduct.getCategory(),
                savedProduct.getPrice(),
                savedProduct.getReleaseDate(),
                savedProduct.isProductAvailable(),
                savedProduct.getStockQuantity()
        );

        // Create and add the semantic document to the vector store
        Document document = new Document(
                UUID.randomUUID().toString(),
                contentToEmbed,
                Map.of("productId", String.valueOf(savedProduct.getId()))
        );

        // Store product data in vector DB
        vectorStore.add(List.of(document));

        return savedProduct;
    }

    public String generateDescription(String name,String category){
        String descPrompt = String.format("""
            Write a concise and professional product description for an e-commerce listing.

            Product Name: %s
            Category: %s

            Keep it simple, engaging, and highlight its primary features or benefits.
            Avoid technical jargon and keep it customer-friendly.
            Limit the description to 250 characters maximum.
            """, name, category);

        // Call AI chat model to generate product description
        return Objects.requireNonNull(chatClient.prompt(descPrompt)
                        .call()
                        .chatResponse())
                .getResult()
                .getOutput()
                .getText();

    }

    public byte[] generateImage(String name,String category,String description) {
        String imagePrompt = String.format("""
                        Generate a highly realistic, professional-grade e-commerce product image.

                        Product Details:
                        - Category: %s
                        - Name: '%s'
                        - Description: %s

                        Requirements:
                          - Use a clean, minimalistic, white or very light grey background.
                          - Ensure the product is well-lit with soft, natural-looking lighting.
                          - Add realistic shadows and soft reflections to ground the product naturally.
                          - No humans, brand logos, watermarks, or text overlays should be visible.
                          - Showcase the product from its most flattering angle that highlights key features.
                          - Ensure the product occupies a prominent position in the frame, centered or slightly off-centered.
                          - Maintain a high resolution and sharpness, ensuring all textures, colors, and details are clear.
                          - Follow the typical visual style of top e-commerce websites like Amazon, Flipkart, or Shopify.
                          - Make the product appear life-like and professionally photographed in a studio setup.
                          - The final image should look immediately ready for use on an e-commerce website without further editing.
                        """, category, name, description);

        // Call AI image model to generate product image
        byte[] aiImage = aiImageGeneratorService.generateImage(imagePrompt);

        return aiImage;
    }

    public void deleteProduct(int id) {
        productRepo.deleteById(id);
    }

    public List<Product> semanticSearchProducts(String userQuery) {
        try {
            // Load prompt template from classpath resource
            String promptTemplate = Files.readString(
                    resourceLoader.getResource("classpath:prompts/product-search-prompt.st")
                            .getFile()
                            .toPath()
            );

            // Fetch related context from vector store using semantic similarity
            String context = fetchSemanticContext(userQuery);

            // Fill variables into the prompt template
            Map<String, Object> variables = new HashMap<>();
            variables.put("userQuery", userQuery);
            variables.put("context", context);

            // Create the full prompt using the template and variables
            PromptTemplate template = PromptTemplate.builder()
                    .template(promptTemplate)
                    .variables(variables)
                    .build();

            Prompt prompt = new Prompt(template.createMessage());

            // Call the AI model to get generated product results
            Generation generation = chatClient.prompt(prompt)
                    .call()
                    .chatResponse()
                    .getResult();

            // Convert the AI's textual output into a list of Product objects
            BeanOutputConverter<List<Product>> outputConverter = new BeanOutputConverter<>(
                    new ParameterizedTypeReference<>() {}
            );
            List<Product> aiProducts = outputConverter.convert(generation.getOutput().getText());

            // Extract valid product IDs (>0) from AI result
            List<Integer> productIds = aiProducts.stream()
                    .map(Product::getId)
                    .filter(id -> id > 0)
                    .toList();

            // Query and return actual product entities from DB using the IDs
            return productRepo.findAllById(productIds);

        } catch (IOException e) {
            // Handle template loading or processing errors
            throw new RuntimeException("Failed to process query", e);
        }
    }

    // Fetch top 5 semantically similar documents using vector store
    private String fetchSemanticContext(String query) {
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(5)                       // Number of similar documents to retrieve
                        .similarityThreshold(0.7f)     // Minimum similarity required
                        .build()
        );

        // Combine contents of retrieved documents into a single context string
        StringBuilder contextBuilder = new StringBuilder();
        for (Document doc : documents) {
            contextBuilder.append(doc.getFormattedContent()).append("\n");
        }

        return contextBuilder.toString();
    }

}
