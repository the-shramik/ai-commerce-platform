package com.telusko.springecomai.service;

import com.telusko.springecomai.model.Order;
import com.telusko.springecomai.model.OrderItem;
import com.telusko.springecomai.model.Product;
import com.telusko.springecomai.model.dto.OrderItemRequest;
import com.telusko.springecomai.model.dto.OrderItemResponse;
import com.telusko.springecomai.model.dto.OrderRequest;
import com.telusko.springecomai.model.dto.OrderResponse;
import com.telusko.springecomai.repo.OrderRepo;
import com.telusko.springecomai.repo.ProductRepo;
import jakarta.transaction.Transactional;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private OrderRepo orderRepo;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private ProductRepo productRepo;

    public OrderResponse placeOrder(OrderRequest request) {
        Order order = new Order();

        // Generate a unique order ID
        String uniqueOrderId = "ORD" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        order.setOrderId(uniqueOrderId);
        order.setCustomerName(request.customerName());
        order.setEmail(request.email());
        order.setOrderDate(LocalDate.now());
        order.setStatus("PLACED");

        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest itemReq : request.items()) {
            Product product = productRepo.findById(itemReq.productId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            if (product.getStockQuantity() < itemReq.quantity()) {
                throw new RuntimeException("Insufficient stock for product: " + product.getName());
            }

            // Deduct stock quantity
            product.setStockQuantity(product.getStockQuantity() - itemReq.quantity());


            // 2. Save updated product
            productRepo.save(product); // make sure this line is included

            String filter = String.format("productId == '%s'", String.valueOf(product.getId()));
            vectorStore.delete(filter);


            // 4. Rebuild the content for embedding
            String updatedContent = String.format("""
                             Product Name: %s
                             Description: %s
                             Brand: %s
                             Category: %s
                             Price: %.2f
                             Release Date: %s
                             Available: %s
                             Stock: %d
                            """,
                    product.getName(),
                    product.getDescription(),
                    product.getBrand(),
                    product.getCategory(),
                    product.getPrice(),
                    product.getReleaseDate(),
                    product.isProductAvailable(),
                    product.getStockQuantity()
            );

            // 5. Create a new vector document
            Document updatedDoc = new Document(
                    UUID.randomUUID().toString(),
                    updatedContent,
                    Map.of("productId", String.valueOf(product.getId()))
            );

            // 6. Add updated document to vector store
            vectorStore.add(List.of(updatedDoc));


            // Build OrderItem and add to list
            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemReq.quantity())
                    .totalPrice(product.getPrice().multiply(BigDecimal.valueOf(itemReq.quantity())))
                    .order(order)
                    .build();

            orderItems.add(item);
        }

        // Set order items and save the order
        order.setItems(orderItems);
        Order savedOrder = orderRepo.save(order);

        // Prepare order summary for vector embedding
        StringBuilder contentToEmbed = new StringBuilder();
        contentToEmbed.append("Order Summary:\n");
        contentToEmbed.append("Order ID: ").append(savedOrder.getOrderId()).append("\n");
        contentToEmbed.append("Customer: ").append(savedOrder.getCustomerName()).append("\n");
        contentToEmbed.append("Email: ").append(savedOrder.getEmail()).append("\n");
        contentToEmbed.append("Date: ").append(savedOrder.getOrderDate()).append("\n");
        contentToEmbed.append("Status: ").append(savedOrder.getStatus()).append("\n");
        contentToEmbed.append("Products:\n");


        // List all products in the order
        for (OrderItem item : savedOrder.getItems()) {
            contentToEmbed.append("- ").append(item.getProduct().getName())
                    .append(" x ").append(item.getQuantity())
                    .append(" = ₹").append(item.getTotalPrice()).append("\n");
        }

        // Create and store vector document
        Document document = new Document(
                UUID.randomUUID().toString(),
                contentToEmbed.toString(),
                Map.of("orderId", savedOrder.getOrderId())
        );

        // Store order data in vector DB
        vectorStore.add(List.of(document));

        // Build response for each order item
        List<OrderItemResponse> itemResponses = savedOrder.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getProduct().getName(),
                        i.getQuantity(),
                        i.getTotalPrice()))
                .collect(Collectors.toList());

        // Return complete order response
        return new OrderResponse(
                savedOrder.getOrderId(),
                savedOrder.getCustomerName(),
                savedOrder.getEmail(),
                savedOrder.getStatus(),
                savedOrder.getOrderDate(),
                itemResponses
        );
    }


    @Transactional
    public List<OrderResponse> getAllOrderResponses() {
        List<OrderResponse> orders = new ArrayList<>();

        orderRepo.findAll().forEach(order -> {
            List<OrderItemResponse> itemResponses = order.getItems().stream()
                    .map(i -> new OrderItemResponse(
                            i.getProduct().getName(),
                            i.getQuantity(),
                            i.getTotalPrice()))
                    .toList();

            orders.add(new OrderResponse(
                    order.getOrderId(),
                    order.getCustomerName(),
                    order.getEmail(),
                    order.getStatus(),
                    order.getOrderDate(),
                    itemResponses));
        });

        return orders;
    }

}
