import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

type MarkdownMessageProps = {
  content: string;
  terminal?: string;
};

export function MarkdownMessage({ content, terminal }: MarkdownMessageProps) {
  return (
    <div className="markdown-message">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a: ({ children, ...props }) => (
            <a {...props} target="_blank" rel="noreferrer">
              {children}
            </a>
          ),
          p: ({ children }) => {
            const text = flattenText(children);
            return <p className={text === "思考中..." ? "markdown-thinking" : undefined}>{children}</p>;
          }
        }}
      >
        {content}
      </ReactMarkdown>
      {terminal ? (
        <p className="terminal-line">
          <strong>{terminal}</strong>
        </p>
      ) : null}
    </div>
  );
}

function flattenText(value: unknown): string {
  if (typeof value === "string" || typeof value === "number") {
    return String(value);
  }
  if (Array.isArray(value)) {
    return value.map(flattenText).join("");
  }
  return "";
}
