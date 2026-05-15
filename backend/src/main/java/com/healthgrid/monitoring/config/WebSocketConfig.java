package com.healthgrid.monitoring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket Configuration for Real-Time Monitoring Updates.
 * 
 * Configures:
 * - STOMP protocol over WebSocket
 * - Message broker for pub/sub messaging
 * - WebSocket endpoints for client connections
 * - Topic destinations for monitoring updates
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configure the message broker.
     * 
     * - enableSimpleBroker: Routes messages to topics and queues
     * - setApplicationDestinationPrefixes: Prefix for client-to-server messages
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple in-memory message broker for /topic destinations
        config.enableSimpleBroker("/topic");
        
        // Set the prefix for destinations sent by the client
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Configure WebSocket endpoints.
     * 
     * Clients connect to /ws endpoint which uses STOMP protocol.
     * Allows all origins for development (configure CORS in production).
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*")  // Configure CORS appropriately for production
                .withSockJS();           // Add fallback for non-WebSocket browsers
    }

}
