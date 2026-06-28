import type { Conversation, ConversationMessage } from "./api/types";

export function taskTitle(task: string, maxLength = 28): string {
  return task.trim().replace(/\s+/g, " ").slice(0, maxLength) || "新对话";
}

export function shouldRetitleConversation(
  conversation: Conversation | undefined,
  messages: ConversationMessage[],
  workspaceKey: string
): boolean {
  if (!conversation) {
    return false;
  }
  if (messages.some((message) => !message.transient && message.role === "USER")) {
    return false;
  }
  const title = conversation.title?.trim();
  return !title || title === "新对话" || title === `${workspaceKey} 新对话`;
}
