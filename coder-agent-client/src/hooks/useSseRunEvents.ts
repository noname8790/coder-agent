import { useEffect, useRef, useState } from "react";
import { normalizeBaseUrl } from "../api/client";
import type { AgentRunEvent } from "../api/types";

export function useSseRunEvents(baseUrl: string, runId?: string) {
  const [events, setEvents] = useState<AgentRunEvent[]>([]);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const seenEventIdsRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    setEvents([]);
    setError(null);
    seenEventIdsRef.current.clear();
    if (!runId) {
      setConnected(false);
      return;
    }

    const source = new EventSource(`${normalizeBaseUrl(baseUrl)}/api/agent-runs/${encodeURIComponent(runId)}/events`);
    source.onopen = () => {
      setConnected(true);
      setError(null);
    };
    source.onerror = () => {
      setConnected(false);
      setError("事件流已断开，可通过运行状态同步结果");
    };

    const eventTypes = [
      "run_created",
      "run_started",
      "assistant_message_started",
      "assistant_delta",
      "assistant_message_completed",
      "assistant_message_cancelled",
      "model_call_started",
      "model_call_completed",
      "model_stream_failure",
      "tool_call_started",
      "tool_call_completed",
      "file_changed",
      "test_reported",
      "git_committed",
      "pr_draft_generated",
      "audit_event",
      "run_finished"
    ];
    const append = (message: MessageEvent) => {
      try {
        const event = JSON.parse(message.data) as AgentRunEvent;
        const eventId = event.eventId || message.lastEventId;
        if (eventId && seenEventIdsRef.current.has(eventId)) {
          return;
        }
        if (eventId) {
          seenEventIdsRef.current.add(eventId);
        }
        setEvents((current) => [...current, event].slice(-300));
      } catch {
        const eventId = message.lastEventId || `${message.type}:${message.data}`;
        if (seenEventIdsRef.current.has(eventId)) {
          return;
        }
        seenEventIdsRef.current.add(eventId);
        setEvents((current) => [
          ...current,
          { eventId: String(Date.now()), runId, type: message.type || "message", payload: { raw: message.data } }
        ].slice(-300));
      }
    };

    source.onmessage = append;
    eventTypes.forEach((type) => source.addEventListener(type, append));
    return () => {
      eventTypes.forEach((type) => source.removeEventListener(type, append));
      source.close();
    };
  }, [baseUrl, runId]);

  return { events, connected, error };
}
