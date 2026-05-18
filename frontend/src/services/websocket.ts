import { Client, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = 'http://localhost:8080/api/v1/ws';

export type WsMessageCallback = (data: unknown) => void;

export class WebSocketService {
  private client: Client;

  constructor(onConnect?: () => void, onError?: (frame: unknown) => void) {
    this.client = new Client({
      webSocketFactory: () => new SockJS(WS_URL) as WebSocket,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    this.client.onConnect = () => {
      console.log('Connected to STOMP');
      onConnect?.();
    };

    this.client.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
      onError?.(frame);
    };
  }

  connect(): void {
    this.client.activate();
  }

  disconnect(): void {
    this.client.deactivate();
  }

  get isConnected(): boolean {
    return this.client.connected;
  }

  subscribeToPatient(
    patientId: string | number,
    callback: WsMessageCallback,
  ): StompSubscription | null {
    if (!this.client.connected) return null;
    return this.client.subscribe(
      `/topic/monitoring/${patientId}`,
      (message) => {
        if (message.body) {
          callback(JSON.parse(message.body));
        }
      },
    );
  }
}
