import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = 'http://localhost:8080/api/v1/ws';

export class WebSocketService {
    constructor(onConnect, onError) {
        this.client = new Client({
            webSocketFactory: () => new SockJS(WS_URL),
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
        });

        this.client.onConnect = () => {
            console.log('Connected to STOMP');
            if (onConnect) onConnect();
        };

        this.client.onStompError = (frame) => {
            console.error('Broker reported error: ' + frame.headers['message']);
            console.error('Additional details: ' + frame.body);
            if (onError) onError(frame);
        };
    }

    connect() {
        this.client.activate();
    }

    disconnect() {
        this.client.deactivate();
    }

    subscribeToPatient(patientId, callback) {
        if (!this.client.connected) return null;
        
        return this.client.subscribe(`/topic/monitoring/${patientId}`, (message) => {
            if (message.body) {
                const data = JSON.parse(message.body);
                callback(data);
            }
        });
    }
}
