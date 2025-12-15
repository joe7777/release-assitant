import axios from 'axios';

const resolveBaseUrl = () => {
  const envBaseUrl =
    import.meta.env.VITE_LLM_HOST_URL || import.meta.env.REACT_APP_LLM_HOST_URL;

  if (envBaseUrl) {
    try {
      const parsed = new URL(envBaseUrl);

      if (parsed.hostname === 'llm-host') {
        const portSuffix = parsed.port ? `:${parsed.port}` : '';
        return `${parsed.protocol}//${window.location.hostname}${portSuffix}`;
      }

      return parsed.toString();
    } catch (error) {
      // ignore parsing errors and fall back to using the browser location
    }
  }

  return `${window.location.protocol}//${window.location.hostname}:8082`;
};

const api = axios.create({
  baseURL: resolveBaseUrl()
});

export type ToolCallTrace = {
  toolName: string;
  argumentsSummary: string;
  durationMs: number;
  success: boolean;
  errorMessage?: string;
};

export type ChatRunResponse = {
  output: string;
  structuredJson?: string;
  toolCalls: ToolCallTrace[];
  toolsUsed: boolean;
};

export async function runPrompt(prompt: string, dryRun: boolean) {
  const response = await api.post<ChatRunResponse>('/chat', { prompt, dryRun });
  return response.data;
}
