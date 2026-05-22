import { Middleware } from '@reduxjs/toolkit';
import { WebSocketService } from '../../services/websocket';
import { patientUpdated, WsPatientUpdate } from '../slices/patientsSlice';
import { liveUpdate, WsDetailUpdate } from '../slices/patientDetailSlice';

// Action type constants
export const WS_CONNECT_MONITORING = 'ws/connectMonitoring';
export const WS_CONNECT_DETAIL = 'ws/connectDetail';
export const WS_DISCONNECT = 'ws/disconnect';

// Action creators
export const wsConnectMonitoring = (patientIds: string[]) => ({
  type: WS_CONNECT_MONITORING as typeof WS_CONNECT_MONITORING,
  payload: patientIds,
});

export const wsConnectDetail = (patientId: string) => ({
  type: WS_CONNECT_DETAIL as typeof WS_CONNECT_DETAIL,
  payload: patientId,
});

export const wsDisconnect = () => ({
  type: WS_DISCONNECT as typeof WS_DISCONNECT,
});

let wsService: WebSocketService | null = null;

const websocketMiddleware: Middleware = (store) => (next) => (action) => {
  const { type, payload } = action as { type: string; payload: unknown };

  switch (type) {
    case WS_CONNECT_MONITORING: {
      if (wsService) wsService.disconnect();

      const patientIds = payload as string[];
      wsService = new WebSocketService(() => {
        patientIds.forEach((id) => {
          wsService?.subscribeToPatient(id, (data) => {
            store.dispatch(patientUpdated(data as WsPatientUpdate));
          });
        });
      });
      wsService.connect();
      break;
    }

    case WS_CONNECT_DETAIL: {
      if (wsService) wsService.disconnect();

      const patientId = payload as string;
      wsService = new WebSocketService(() => {
        wsService?.subscribeToPatient(patientId, (data) => {
          store.dispatch(liveUpdate(data as WsDetailUpdate));
        });
      });
      wsService.connect();
      break;
    }

    case WS_DISCONNECT: {
      if (wsService) {
        wsService.disconnect();
        wsService = null;
      }
      break;
    }
  }

  return next(action);
};

export default websocketMiddleware;
